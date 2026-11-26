package it.evadid.workbook.elements.interactionElements.table

import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.meta.ExecutionContextInformation
import scala.concurrent.Future
import upickle.default.ReadWriter

/** Checks one editable cell on request; `expected` lists the accepted spellings. */
case class TableCellGrader(index: Int, expected: Seq[String]) extends WorkbookInteractionGrader[TableAnswer, Unit] derives ReadWriter {
  require(index >= 0 && expected.nonEmpty, "A table grader needs a cell and accepted answers")
  override val importance = GradingImportance.REQUIRED_TEST
  override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
  override val gradingEntity = FeedbackEntity.TestEntity
  override protected def handleGrading(state: TableAnswer, context: ExecutionContextInformation) = Future {
    require(state.values.indices.contains(index), "Stored table dimensions do not match the exercise")
    GradingResult(this, state, if TableCellGrader.accepts(expected, state.values(index)) then GradingStatus.Correct else GradingStatus.Incorrect)
  }(using context.futureExecutionContext)
}

object TableCellGrader {
  /** Whether `value` is one of the `expected` spellings; the grade and the marked cells
    * ([[AnswerTableInteraction.wrongParts]]) both follow it. */
  def accepts(expected: Seq[String], value: String): Boolean = expected.exists(_.trim == value.trim)
}
