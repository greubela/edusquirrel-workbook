package it.evadid.workbook.interaction.variable.events

import it.evadid.workbook.abstractions.InteractionFormat

import java.time.LocalDateTime
import upickle.default.*

/** Client identities survive serialization and retries; timestamps never determine identity. */
case class InteractionEvents[F <: InteractionFormat](values: Set[InteractionEvent[F]]) {
  require(values.map(_.eventId).size == values.size, "History contains conflicting events with the same eventId")

  def updated(event: InteractionEvent[F]): InteractionEvents[F] =
    copy(values = values.filterNot(_.eventId == event.eventId) + event)

  lazy val timestampLastChanged: Option[LocalDateTime] = values.map(_.timestamp).maxOption

  def merge(other: InteractionEvents[F]): InteractionEvents[F] =
    other.values.foldLeft(this)(_.updated(_))
}

object InteractionEvents {
  def empty[F <: InteractionFormat]: InteractionEvents[F] = InteractionEvents(Set.empty)
}
