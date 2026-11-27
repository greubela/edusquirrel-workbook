package it.evadid.workbook.interaction.variable.events

import upickle.default.*

/** Client-generated identity of one immutable interaction event: new content requires a new ID. */
case class EventId(value: String) {
  require(scala.util.Try(java.util.UUID.fromString(value).toString == value).getOrElse(false),
    s"Interaction eventId must be a canonical UUID: $value")
  override def toString: String = value
}

object EventId {
  def random(): EventId = EventId(java.util.UUID.randomUUID().toString)

  given ReadWriter[EventId] = readwriter[String].bimap(_.value, EventId(_))
}
