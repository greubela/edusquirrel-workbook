package it.evadid.workbook.reports

import it.evadid.workbook.abstractions.StateReference
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.abstractions.grading.{GraderRunningBehavior, GradingStatus}
import it.evadid.workbook.history.InteractionHistory

import java.time.Instant

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
final case class CheckReport(tracked: Boolean, failedChecks: Int, wrongParts: Set[Int])

object CheckReport {
  val untracked: CheckReport = CheckReport(tracked = false, 0, Set.empty)

  private case class Check(answer: StateReference, time: Instant, passed: Boolean)

  private def isAssessment(status: GradingStatus): Boolean = status match {
    case GradingStatus.Correct | GradingStatus.Incorrect | GradingStatus.PartiallyCorrect => true
    case _ => false
  }

  def apply[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], history: InteractionHistory[F]): CheckReport = {
    val graders = interaction.gradingElements
    if !graders.exists(_.runningBehavior == GraderRunningBehavior.RUN_ONLY_MANUALLY) then untracked
    else {
      val references = graders.map(_.reference).toSet
      val checks = history.gradingRecords.toSeq.flatMap { (answer, byGrader) =>
        val results = byGrader.view.filterKeys(references).values.toSeq
        Option.when(results.map(_.grader).toSet == references && results.forall(result => isAssessment(result.gradingStatus)))(
          Check(answer, results.map(_.gradingTime).reduce((a, b) => if a.isAfter(b) then a else b),
            results.forall(_.gradingStatus.isCorrect)))
      }
      val firstPassed = checks.filter(_.passed).map(_.time).reduceOption((a, b) => if a.isBefore(b) then a else b)
      val failed = checks.filterNot(_.passed)
      CheckReport(tracked = true, failed.count(check => firstPassed.forall(check.time.isBefore)),
        history.latestAnswer.map(_.value).toSeq.flatMap(current =>
          if failed.exists(_.answer == history.referenceOf(current)) then interaction.wrongParts(current) else Nil).toSet)
    }
  }
}
