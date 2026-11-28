package it.evadid.workbook.model.history.events

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.sync.UpdateImportance

import java.time.LocalDateTime
import upickle.default.*

/** Events are immutable: an [[EventId]] names exactly one content, and a revision is a new event. */
sealed trait InteractionEvent[F <: InteractionFormat] {
  def eventId: EventId
  def associatedState: InteractionFormat.State[F]
  def timestamp: LocalDateTime
  def updateImportance: UpdateImportance
}

case class AnswerChangedEvent[F <: InteractionFormat](
    newState: InteractionFormat.State[F],
    timestamp: LocalDateTime,
    updateImportance: UpdateImportance,
    eventId: EventId = EventId.random()
) extends InteractionEvent[F] {
  override def associatedState: InteractionFormat.State[F] = newState
}

sealed trait GradingEvent[F <: InteractionFormat] extends InteractionEvent[F] {
  def grader: WorkbookInteractionGrader[InteractionFormat.State[F], InteractionFormat.Grading[F]]
}

case class GradingStartedEvent[F <: InteractionFormat](
    associatedState: InteractionFormat.State[F],
    timestamp: LocalDateTime,
    grader: WorkbookInteractionGrader[InteractionFormat.State[F], InteractionFormat.Grading[F]],
    updateImportance: UpdateImportance = UpdateImportance.TEMPORARY,
    eventId: EventId = EventId.random()
) extends GradingEvent[F]

/** The result is the single source of truth for the answer, grader and completion time. */
case class GradingCompletedEvent[F <: InteractionFormat](
    result: GradingResult[InteractionFormat.State[F], InteractionFormat.Grading[F]],
    updateImportance: UpdateImportance = UpdateImportance.MAJOR,
    eventId: EventId = EventId.random()
) extends GradingEvent[F] {
  require(result.gradingStatus != GradingStatus.NotRequested && result.gradingStatus != GradingStatus.NotFinished,
    "A completed grading event requires a completed assessment or execution error")
  override def associatedState: InteractionFormat.State[F] = result.gradedState
  override def timestamp: LocalDateTime = result.gradingTime
  override def grader: WorkbookInteractionGrader[InteractionFormat.State[F], InteractionFormat.Grading[F]] = result.grader
}

sealed trait ScaffoldingEvent[F <: InteractionFormat] extends InteractionEvent[F] {
  def scaffoldingEntity: FeedbackEntity
}

case class ScaffoldingStartedEvent[F <: InteractionFormat](
    associatedState: InteractionFormat.State[F],
    timestamp: LocalDateTime,
    scaffoldingEntity: FeedbackEntity,
    updateImportance: UpdateImportance = UpdateImportance.TEMPORARY,
    eventId: EventId = EventId.random()
) extends ScaffoldingEvent[F]

case class ScaffoldingCompletedEvent[F <: InteractionFormat](
    result: ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]],
    updateImportance: UpdateImportance = UpdateImportance.MAJOR,
    eventId: EventId = EventId.random()
) extends ScaffoldingEvent[F] {
  require(result.scaffoldingStatus != ScaffoldingStatus.ScaffoldingRequestedButNotFinished(),
    "A completed scaffolding event requires feedback or an execution error")
  override def associatedState: InteractionFormat.State[F] = result.forState
  override def timestamp: LocalDateTime = result.scaffoldingTime
  override def scaffoldingEntity: FeedbackEntity = result.scaffoldingEntity
}
