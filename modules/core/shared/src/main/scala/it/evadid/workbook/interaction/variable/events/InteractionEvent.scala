package it.evadid.workbook.interaction.variable.events

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.interaction.sync.UpdateImportance
import java.time.LocalDateTime
import upickle.default.*

sealed trait InteractionEvent[T, GA, SA] derives ReadWriter {
  def associatedState: T
  def timestamp: LocalDateTime
  def updateImportance: UpdateImportance
}

case class AnswerChangedEvent[T, GA, SA](
    newState: T,
    timestamp: LocalDateTime,
    updateImportance: UpdateImportance
) extends InteractionEvent[T, GA, SA] derives ReadWriter {
  override def associatedState: T = newState
}

sealed trait GradingEvent[T, GA, SA] extends InteractionEvent[T, GA, SA] derives ReadWriter {
  def grader: WorkbookInteractionGrader[T, GA]
}

case class GradingStartedEvent[T, GA, SA](
    associatedState: T,
    timestamp: LocalDateTime,
    grader: WorkbookInteractionGrader[T, GA],
    updateImportance: UpdateImportance = UpdateImportance.TEMPORARY
) extends GradingEvent[T, GA, SA] derives ReadWriter

/** The result is the single source of truth for the answer, grader and completion time. */
case class GradingCompletedEvent[T, GA, SA](
    result: GradingResult[T, GA],
    updateImportance: UpdateImportance = UpdateImportance.MAJOR
) extends GradingEvent[T, GA, SA] derives ReadWriter {
  require(result.gradingStatus != GradingStatus.NotRequested() && result.gradingStatus != GradingStatus.NotFinished(),
    "A completed grading event requires a completed assessment or execution error")
  override def associatedState: T = result.gradedState
  override def timestamp: LocalDateTime = result.gradingTime
  override def grader: WorkbookInteractionGrader[T, GA] = result.grader
}

sealed trait ScaffoldingEvent[T, GA, SA] extends InteractionEvent[T, GA, SA] derives ReadWriter {
  def scaffoldingEntity: FeedbackEntity
}

case class ScaffoldingStartedEvent[T, GA, SA](
    associatedState: T,
    timestamp: LocalDateTime,
    scaffoldingEntity: FeedbackEntity,
    updateImportance: UpdateImportance = UpdateImportance.TEMPORARY
) extends ScaffoldingEvent[T, GA, SA] derives ReadWriter

case class ScaffoldingCompletedEvent[T, GA, SA](
    result: ScaffoldingResult[T, SA],
    updateImportance: UpdateImportance = UpdateImportance.MAJOR
) extends ScaffoldingEvent[T, GA, SA] derives ReadWriter {
  require(result.scaffoldingStatus != ScaffoldingStatus.ScaffoldingRequestedButNotFinished(),
    "A completed scaffolding event requires feedback or an execution error")
  override def associatedState: T = result.forState
  override def timestamp: LocalDateTime = result.scaffoldingTime
  override def scaffoldingEntity: FeedbackEntity = result.scaffoldingEntity
}
