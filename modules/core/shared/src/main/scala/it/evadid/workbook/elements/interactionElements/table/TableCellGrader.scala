package it.evadid.workbook.elements.interactionElements.table

import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.meta.ExecutionContextInformation
import scala.concurrent.Future
import upickle.default.ReadWriter

case class TableCellGrader(index: Int, expected: Seq[String]) extends WorkbookInteractionGrader[TableAnswer, Unit] derives ReadWriter {
  require(index >= 0 && expected.nonEmpty, "A table grader needs a cell and accepted answers")
  override val importance = GradingImportance.REQUIRED_TEST
  override val runningBehavior = GraderRunningBehavior.RUN_ALWAYS
  override val gradingEntity = FeedbackEntity.TestEntity()
  override protected def handleGrading(state: TableAnswer, context: ExecutionContextInformation) = Future {
    require(state.values.indices.contains(index), "Stored table dimensions do not match the exercise")
    val correct = expected.exists(_.trim == state.values(index).trim)
    GradingResult(this, state, if correct then GradingStatus.Correct() else GradingStatus.Incorrect())
  }(using context.futureExecutionContext)
}
