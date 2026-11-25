package it.evadid.workbook.elements.interactionElements.matching

import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.meta.ExecutionContextInformation
import java.time.LocalDateTime
import scala.concurrent.Future
import upickle.default.ReadWriter

/** Grades a whole matching task on request. The additional data is the number of correct connections for the
 * feedback text; the wrong connections are marked by the renderer (see [[MatchingInteraction.wrongParts]]).
 */
case class MatchingGrader(expected: Seq[Int], rightCount: Int)
  extends WorkbookInteractionGrader[MatchingInteractionState, Int] derives ReadWriter {
  override val importance = GradingImportance.REQUIRED_TEST
  override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
  override val gradingEntity = FeedbackEntity.TestEntity()

  override protected def handleGrading(state: MatchingInteractionState, context: ExecutionContextInformation): Future[GradingResult[MatchingInteractionState, Int]] = Future {
    val correct = MatchingGrader.correctCount(expected, rightCount, state)
    GradingResult(this, state, MatchingGrader.status(correct, expected.size), None, Some(correct), LocalDateTime.now())
  }(using context.futureExecutionContext)
}

object MatchingGrader {
  /** Per left item: `None` while unconnected, else whether its connection is the expected one. The grade and the
    * marks of wrong connections ([[MatchingInteraction.wrongParts]]) are both derived from it. */
  def verdicts(expected: Seq[Int], rightCount: Int, state: MatchingInteractionState): Seq[Option[Boolean]] =
    state.sanitized(expected.size, rightCount).selectedRightIndicesByLeftIndex.zip(expected)
      .map((selected, answer) => selected.map(_ == answer))

  def correctCount(expected: Seq[Int], rightCount: Int, state: MatchingInteractionState): Int =
    verdicts(expected, rightCount, state).count(_.contains(true))

  /** Left items connected with a wrong category; unconnected items are no mistakes. */
  def wrongConnections(expected: Seq[Int], rightCount: Int, state: MatchingInteractionState): Set[Int] =
    verdicts(expected, rightCount, state).zipWithIndex.collect { case (Some(false), index) => index }.toSet

  def status(correct: Int, total: Int): GradingStatus =
    if correct == total then GradingStatus.Correct()
    else if correct == 0 then GradingStatus.Incorrect()
    else GradingStatus.PartiallyCorrect()
}
