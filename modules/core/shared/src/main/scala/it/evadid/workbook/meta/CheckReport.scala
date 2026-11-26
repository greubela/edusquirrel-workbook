package it.evadid.workbook.meta

import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.abstractions.grading.{GraderRunningBehavior, GradingStatus}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory

import java.time.LocalDateTime

/** Checks requested by the learner, derived from the grading history of an interaction.
 *
 * A check is an answer that every grader assessed. Only interactions with a grader that runs on request only are
 * checked: their complete assessments stem from the check button, whereas automatically graded interactions
 * assess every edit. Assessments with execution errors are no checks, as the learner made no mistake.
 *
 * @param tracked       whether the interaction is checked on request
 * @param failedChecks  failed checks before the first passed one (all failed checks while none passed)
 * @param wrongParts    the parts that a failed check found wrong in the current answer, see
 *                     [[WorkbookInteractionElement.wrongParts]]; empty as soon as the answer changed
 */
final case class CheckReport(tracked: Boolean, failedChecks: Int, wrongParts: Set[Int]) derives upickle.default.ReadWriter

object CheckReport {
  val untracked: CheckReport = CheckReport(tracked = false, 0, Set.empty)

  private case class Check[T](answer: T, time: LocalDateTime, passed: Boolean)

  private def isAssessment(status: GradingStatus): Boolean = status match {
    case GradingStatus.Correct() | GradingStatus.Incorrect() | GradingStatus.PartiallyCorrect() => true
    case _ => false
  }

  def apply[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], history: InteractionVariableHistory[F]): CheckReport = {
    val graders = interaction.gradingElements
    if !graders.exists(_.runningBehavior == GraderRunningBehavior.RUN_ONLY_MANUALLY) then untracked
    else {
      val keys = graders.map(_.key).toSet
      val checks = history.historyGrading.gradingsFinished.toSeq.flatMap { graded =>
        val results = graded.graderResultMap.values.filter(result => keys(result.grader.key)).toSeq
        Option.when(results.map(_.grader.key).toSet == keys && results.forall(result => isAssessment(result.gradingStatus)))(
          Check(graded.state, results.map(_.gradingTime).reduce((a, b) => if a.isAfter(b) then a else b),
            results.forall(_.gradingStatus.isCorrect)))
      }
      val firstPassed = checks.filter(_.passed).map(_.time).reduceOption((a, b) => if a.isBefore(b) then a else b)
      val failed = checks.filterNot(_.passed)
      CheckReport(tracked = true, failed.count(check => firstPassed.forall(check.time.isBefore)),
        history.historyStates.lastStateOption.map(_.value).toSeq.flatMap(current =>
          failed.filter(_.answer == current).flatMap(check => interaction.wrongParts(check.answer))).toSet)
    }
  }
}
