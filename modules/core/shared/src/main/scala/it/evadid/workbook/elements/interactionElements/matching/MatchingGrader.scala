package it.evadid.workbook.elements.interactionElements.matching

import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.meta.ExecutionContextInformation
import java.time.LocalDateTime
import scala.concurrent.Future
import upickle.default.ReadWriter

/** Grades a whole matching task on request. The additional data is the number of correct connections, so the
 * feedback can say how many are right without revealing which ones are wrong.
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
  def correctCount(expected: Seq[Int], rightCount: Int, state: MatchingInteractionState): Int =
    state.sanitized(expected.size, rightCount).selectedRightIndicesByLeftIndex.zip(expected)
      .count((selected, answer) => selected.contains(answer))

  def status(correct: Int, total: Int): GradingStatus =
    if correct == total then GradingStatus.Correct()
    else if correct == 0 then GradingStatus.Incorrect()
    else GradingStatus.PartiallyCorrect()
}
