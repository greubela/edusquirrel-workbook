package it.evadid.workbook.history.events

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{FeedbackEntity, GraderReference, InteractionFormat, StateReference}
import it.evadid.workbook.abstractions.grading.{GradingRecord, GradingStatus}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingRecord, ScaffoldingStatus}
import it.evadid.workbook.sync.UpdateImportance

import java.time.LocalDateTime
import upickle.default.*
import upickle.implicits.key

/** Events are immutable: an [[EventId]] names exactly one content, and a revision is a new event. Saved events
  * are tagged with their fixed `@key` names, so the classes can be renamed. Only answer events hold an answer;
  * feedback events refer to it by [[StateReference]] and to graders by [[GraderReference]].
  */
sealed trait InteractionEvent[F <: InteractionFormat] {
  def eventId: EventId
  def timestamp: LocalDateTime
  def updateImportance: UpdateImportance
}

/** Grading and scaffolding events, which refer to their answer. */
sealed trait FeedbackEvent[F <: InteractionFormat] extends InteractionEvent[F] {
  def state: StateReference
}

@key("AnswerChangedEvent")
case class AnswerChangedEvent[F <: InteractionFormat](
    newState: InteractionFormat.State[F],
    timestamp: LocalDateTime,
    updateImportance: UpdateImportance,
    eventId: EventId = EventId.random()
) extends InteractionEvent[F]

sealed trait GradingEvent[F <: InteractionFormat] extends FeedbackEvent[F] {
  def grader: GraderReference
}

@key("GradingStartedEvent")
case class GradingStartedEvent[F <: InteractionFormat](
    state: StateReference,
    timestamp: LocalDateTime,
    grader: GraderReference,
    updateImportance: UpdateImportance = UpdateImportance.TEMPORARY,
    eventId: EventId = EventId.random()
) extends GradingEvent[F]

/** The record is the single source of truth for the answer, grader and completion time. */
@key("GradingCompletedEvent")
case class GradingCompletedEvent[F <: InteractionFormat](
    record: GradingRecord[InteractionFormat.Grading[F]],
    updateImportance: UpdateImportance = UpdateImportance.MAJOR,
    eventId: EventId = EventId.random()
) extends GradingEvent[F] {
  require(record.gradingStatus != GradingStatus.NotRequested && record.gradingStatus != GradingStatus.NotFinished,
    "A completed grading event requires a completed assessment or execution error")
  override def state: StateReference = record.state
  override def timestamp: LocalDateTime = record.gradingTime
  override def grader: GraderReference = record.grader
}

sealed trait ScaffoldingEvent[F <: InteractionFormat] extends FeedbackEvent[F] {
  def scaffoldingEntity: FeedbackEntity
}

@key("ScaffoldingStartedEvent")
case class ScaffoldingStartedEvent[F <: InteractionFormat](
    state: StateReference,
    timestamp: LocalDateTime,
    scaffoldingEntity: FeedbackEntity,
    updateImportance: UpdateImportance = UpdateImportance.TEMPORARY,
    eventId: EventId = EventId.random()
) extends ScaffoldingEvent[F]

@key("ScaffoldingCompletedEvent")
case class ScaffoldingCompletedEvent[F <: InteractionFormat](
    record: ScaffoldingRecord[InteractionFormat.Scaffolding[F]],
    updateImportance: UpdateImportance = UpdateImportance.MAJOR,
    eventId: EventId = EventId.random()
) extends ScaffoldingEvent[F] {
  require(record.scaffoldingStatus != ScaffoldingStatus.ScaffoldingRequestedButNotFinished(),
    "A completed scaffolding event requires feedback or an execution error")
  override def state: StateReference = record.state
  override def timestamp: LocalDateTime = record.scaffoldingTime
  override def scaffoldingEntity: FeedbackEntity = record.scaffoldingEntity
}
