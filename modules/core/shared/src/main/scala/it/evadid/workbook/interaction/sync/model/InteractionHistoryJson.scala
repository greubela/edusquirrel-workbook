package it.evadid.workbook.interaction.sync.model

import it.evadid.core.util.io.ParsingContext
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.EventSyncStrategy
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import java.time.LocalDateTime
import upickle.default.*

/** Self-describing transport and storage boundary; decoding never needs a workbook registry. */
case class InteractionHistoryJson(format: InteractionFormat, historyJson: String) derives ReadWriter {
  def toTyped: InteractionVariableHistory[format.type] = typedAs(format)

  def typedAs[F <: InteractionFormat](expected: F): InteractionVariableHistory[F] = {
    require(format == expected, s"History format ${format.name} does not match ${expected.name}")
    ParsingContext(s"interaction history (${format.name})", historyJson) {
      InteractionFormat.codecsFor(expected).historySerializer.deserialize(historyJson)
    }
  }

  def merge(other: InteractionHistoryJson): InteractionHistoryJson =
    InteractionHistoryJson.fromHistory(format, toTyped.mergeWith(other.typedAs(format)))

  def cleanWithStrategy(strategy: EventSyncStrategy): InteractionHistoryJson =
    InteractionHistoryJson.fromHistory(format, toTyped.cleanWithStrategy(strategy))

  def between(starting: LocalDateTime, until: LocalDateTime): InteractionHistoryJson = {
    require(!until.isBefore(starting), "History range end must not precede its start")
    val history = toTyped
    InteractionHistoryJson.fromHistory(format, history.copy(events = history.events.copy(values =
      history.events.values.filter(event => !event.timestamp.isBefore(starting) && !event.timestamp.isAfter(until)))))
  }

  def serialized: String = write(this)
}

object InteractionHistoryJson {
  def fromHistory[F <: InteractionFormat](format: F, history: InteractionVariableHistory[F]): InteractionHistoryJson =
    InteractionHistoryJson(format, InteractionFormat.codecsFor(format).historySerializer.serialize(history))

  def deserialize(value: String): InteractionHistoryJson = ParsingContext("interaction history envelope", value) {
    val decoded = read[InteractionHistoryJson](value)
    decoded.toTyped // Validate at the storage boundary, including events and feedback.
    decoded
  }

  def mergeSerialized(histories: Iterable[String]): String =
    histories.iterator.map(deserialize).reduce(_.merge(_)).serialized
}
