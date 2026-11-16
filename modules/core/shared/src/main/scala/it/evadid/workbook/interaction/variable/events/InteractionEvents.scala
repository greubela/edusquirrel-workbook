package it.evadid.workbook.interaction.variable.events

import java.time.LocalDateTime
import upickle.default.*

/** Exact duplicates are idempotent; different events at the same timestamp are retained. */
case class InteractionEvents[T, GA, SA](values: Set[InteractionEvent[T, GA, SA]]) derives ReadWriter {
  lazy val timestampLastChanged: Option[LocalDateTime] = values.map(_.timestamp).maxOption

  def merge(other: InteractionEvents[T, GA, SA]): InteractionEvents[T, GA, SA] =
    copy(values = values ++ other.values)
}

object InteractionEvents {
  def empty[T, GA, SA]: InteractionEvents[T, GA, SA] = InteractionEvents(Set.empty)
}
