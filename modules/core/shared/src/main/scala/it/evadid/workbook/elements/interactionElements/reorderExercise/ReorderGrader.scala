package it.evadid.workbook.elements.interactionElements.reorderExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.meta.ExecutionContextInformation
import scala.concurrent.Future
import upickle.default.ReadWriter

private[reorderExercise] object ReorderGrading {
  def status(order: Seq[Int], constraints: Seq[(Int, Int)]): GradingStatus = {
    val positions = order.zipWithIndex.toMap
    val correct = if constraints.isEmpty then order == order.indices else
      constraints.forall((before, after) => positions(before) < positions(after))
    if correct then GradingStatus.Correct() else GradingStatus.Incorrect()
  }
}

case class ReorderCodeGrader(lines: Seq[String], orderConstraints: Seq[(Int, Int)])
    extends WorkbookInteractionGrader[ReorderInteractionState[String], Unit] derives ReadWriter {
  override val importance = GradingImportance.REQUIRED_TEST
  override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
  override val gradingEntity = FeedbackEntity.TestEntity()
  override protected def handleGrading(state: ReorderInteractionState[String], context: ExecutionContextInformation) = Future {
    require(state.elements == lines && state.correctOrder == lines.indices, "Stored reorder content does not match the exercise")
    GradingResult(this, state, ReorderGrading.status(state.currentOrder, orderConstraints))
  }(using context.futureExecutionContext)
}

case class ReorderMapIdGrader(ids: Seq[LanguageMapContentId])
    extends WorkbookInteractionGrader[ReorderInteractionState[LanguageMapContentId], Unit] derives ReadWriter {
  override val importance = GradingImportance.REQUIRED_TEST
  override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
  override val gradingEntity = FeedbackEntity.TestEntity()
  override protected def handleGrading(state: ReorderInteractionState[LanguageMapContentId], context: ExecutionContextInformation) = Future {
    require(state.elements == ids && state.correctOrder == ids.indices, "Stored reorder content does not match the exercise")
    GradingResult(this, state, ReorderGrading.status(state.currentOrder, Seq.empty))
  }(using context.futureExecutionContext)
}
