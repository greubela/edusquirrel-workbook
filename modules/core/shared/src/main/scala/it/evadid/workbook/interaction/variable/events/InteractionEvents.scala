package it.evadid.workbook.interaction.variable.events

import it.evadid.workbook.abstractions.InteractionFormat

import java.time.LocalDateTime
import upickle.default.*

/** Exact duplicates are idempotent; different events at the same timestamp are retained. */
case class InteractionEvents[F <: InteractionFormat](values: Set[InteractionEvent[F]]) {
  lazy val timestampLastChanged: Option[LocalDateTime] = values.map(_.timestamp).maxOption

  def merge(other: InteractionEvents[F]): InteractionEvents[F] =
    copy(values = values ++ other.values)
}

object InteractionEvents {
  def empty[F <: InteractionFormat]: InteractionEvents[F] = InteractionEvents(Set.empty)
}
