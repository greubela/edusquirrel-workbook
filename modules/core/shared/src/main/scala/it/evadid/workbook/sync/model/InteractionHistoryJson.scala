package it.evadid.workbook.sync.model

import it.evadid.core.util.io.ParsingContext
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.sync.EventSyncStrategy
import it.evadid.workbook.history.InteractionVariableHistory
import it.evadid.workbook.history.events.EventId
import java.time.Instant
import upickle.default.*

/** Self-describing transport and storage boundary; decoding never needs a workbook registry. The history is nested
  * as JSON, not as an escaped string.
  */
case class InteractionHistoryJson(format: InteractionFormat, historyJson: ujson.Value) derives ReadWriter {
  def toTyped: InteractionVariableHistory[format.type] = typedAs(format)

  def typedAs[F <: InteractionFormat](expected: F): InteractionVariableHistory[F] = {
    require(format == expected, s"History format ${format.name} does not match ${expected.name}")
    ParsingContext(s"interaction history (${format.name})", historyJson.render()) {
      read[InteractionVariableHistory[F]](historyJson)(using InteractionFormat.codecsFor(expected).historyReadWriter)
    }
  }

  def eventIds: Set[EventId] = toTyped.events.ids

  /** Stored records merge additively; superseded DEFAULT and TEMPORARY answers do not accumulate. */
  def merge(other: InteractionHistoryJson): InteractionHistoryJson =
    InteractionHistoryJson.fromHistory(format, toTyped.mergeWith(other.typedAs(format)).cleanedHistory())

  def cleanWithStrategy(strategy: EventSyncStrategy): InteractionHistoryJson =
    InteractionHistoryJson.fromHistory(format, toTyped.cleanWithStrategy(strategy))

  def between(starting: Instant, until: Instant): InteractionHistoryJson = {
    require(!until.isBefore(starting), "History range end must not precede its start")
    val history = toTyped
    InteractionHistoryJson.fromHistory(format, history.copy(events = history.events.copy(values =
      history.events.values.filter(event => !event.timestamp.isBefore(starting) && !event.timestamp.isAfter(until)))))
  }

  def serialized: String = write(this)
}

object InteractionHistoryJson {
  def fromHistory[F <: InteractionFormat](format: F, history: InteractionVariableHistory[F]): InteractionHistoryJson =
    InteractionHistoryJson(format, writeJs(history)(using InteractionFormat.codecsFor(format).historyReadWriter))

  /** An envelope for a history read as text, e.g. from a database column; malformed JSON fails here. */
  def apply(format: InteractionFormat, historyJson: String): InteractionHistoryJson =
    ParsingContext(s"interaction history (${format.name})", historyJson)(InteractionHistoryJson(format, ujson.read(historyJson)))

  def deserialize(value: String): InteractionHistoryJson = ParsingContext("interaction history envelope", value) {
    val decoded = read[InteractionHistoryJson](value)
    decoded.toTyped // Validate at the storage boundary, including events and feedback.
    decoded
  }

  /** The storage read rule: each stored record is decoded and narrowed to the strategy's selection within the
    * inclusive window. A record that cannot be read is reported and left out, so that it cannot hide the others.
    */
  def readable[K, S](stored: Iterable[(K, S)])(decode: S => InteractionHistoryJson, strategy: EventSyncStrategy,
      starting: Instant, until: Instant, discarded: (K, Throwable) => Unit): Map[K, InteractionHistoryJson] =
    stored.flatMap { (key, value) =>
      scala.util.Try(decode(value).cleanWithStrategy(strategy).between(starting, until)).fold(
        error => { discarded(key, error); None }, history => Some(key -> history))
    }.toMap

  def mergeSerialized(histories: Iterable[String]): String =
    histories.iterator.map(deserialize).reduce(_.merge(_)).serialized
}
