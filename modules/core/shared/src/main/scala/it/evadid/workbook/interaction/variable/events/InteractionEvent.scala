package it.evadid.workbook.interaction.variable.events

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.interaction.sync.UpdateImportance

import java.time.LocalDateTime
import upickle.default.*

sealed trait InteractionEvent[F <: InteractionFormat] derives ReadWriter {
  def associatedState: F#InteractionStateType
  def timestamp: LocalDateTime
  def updateImportance: UpdateImportance
}

case class AnswerChangedEvent[F <: InteractionFormat](
    newState: F#InteractionStateType,
    timestamp: LocalDateTime,
    updateImportance: UpdateImportance
) extends InteractionEvent[F] derives ReadWriter {
  override def associatedState: F#InteractionStateType = newState
}

sealed trait GradingEvent[F <: InteractionFormat] extends InteractionEvent[F] derives ReadWriter {
  def grader: WorkbookInteractionGrader[F]
}

case class GradingStartedEvent[F <: InteractionFormat](
    associatedState: F#InteractionStateType,
    timestamp: LocalDateTime,
    grader: WorkbookInteractionGrader[F],
    updateImportance: UpdateImportance = UpdateImportance.TEMPORARY
) extends GradingEvent[F] derives ReadWriter

/** The result is the single source of truth for the answer, grader and completion time. */
case class GradingCompletedEvent[F <: InteractionFormat](
    result: GradingResult[F],
    updateImportance: UpdateImportance = UpdateImportance.MAJOR
) extends GradingEvent[F] derives ReadWriter {
  require(result.gradingStatus != GradingStatus.NotRequested() && result.gradingStatus != GradingStatus.NotFinished(),
    "A completed grading event requires a completed assessment or execution error")
  override def associatedState: F#InteractionStateType = result.gradedState
  override def timestamp: LocalDateTime = result.gradingTime
  override def grader: WorkbookInteractionGrader[F] = result.grader
}

sealed trait ScaffoldingEvent[F <: InteractionFormat] extends InteractionEvent[F] derives ReadWriter {
  def scaffoldingEntity: FeedbackEntity
}

case class ScaffoldingStartedEvent[F <: InteractionFormat](
    associatedState: F#InteractionStateType,
    timestamp: LocalDateTime,
    scaffoldingEntity: FeedbackEntity,
    updateImportance: UpdateImportance = UpdateImportance.TEMPORARY
) extends ScaffoldingEvent[F] derives ReadWriter

case class ScaffoldingCompletedEvent[F <: InteractionFormat](
    result: ScaffoldingResult[F],
    updateImportance: UpdateImportance = UpdateImportance.MAJOR
) extends ScaffoldingEvent[F] derives ReadWriter {
  require(result.scaffoldingStatus != ScaffoldingStatus.ScaffoldingRequestedButNotFinished(),
    "A completed scaffolding event requires feedback or an execution error")
  override def associatedState: F#InteractionStateType = result.forState
  override def timestamp: LocalDateTime = result.scaffoldingTime
  override def scaffoldingEntity: FeedbackEntity = result.scaffoldingEntity
}
