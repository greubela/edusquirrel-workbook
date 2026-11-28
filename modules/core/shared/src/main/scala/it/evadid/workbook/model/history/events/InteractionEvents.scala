package it.evadid.workbook.model.history.events

import it.evadid.workbook.abstractions.InteractionFormat

import java.time.LocalDateTime
import upickle.default.*

/** Client identities survive serialization and retries; timestamps never determine identity.
  * Events are immutable, so adding an equal event is a no-op and a different event under a known ID is rejected.
  */
case class InteractionEvents[F <: InteractionFormat](values: Set[InteractionEvent[F]]) {
  require(ids.size == values.size, "Interaction events are immutable: different events share an eventId")

  lazy val ids: Set[EventId] = values.map(_.eventId)

  def updated(event: InteractionEvent[F]): InteractionEvents[F] = copy(values = values + event)

  lazy val timestampLastChanged: Option[LocalDateTime] = values.map(_.timestamp).maxOption

  def merge(other: InteractionEvents[F]): InteractionEvents[F] = copy(values = values ++ other.values)
}

object InteractionEvents {
  /** The events tied for the latest timestamp (empty for no events). */
  def latestTies[E <: InteractionEvent[?]](events: Set[E]): Set[E] =
    events.map(_.timestamp).maxOption.fold(Set.empty[E])(latest => events.filter(_.timestamp == latest))

  def empty[F <: InteractionFormat]: InteractionEvents[F] = InteractionEvents(Set.empty)
}
