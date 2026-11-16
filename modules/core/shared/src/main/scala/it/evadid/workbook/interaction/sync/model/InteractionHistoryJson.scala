package it.evadid.workbook.interaction.sync.model

import it.evadid.core.util.io.ParsingContext
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.InteractionFormat
import it.evadid.workbook.interaction.sync.EventSyncStrategy
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import java.time.LocalDateTime
import upickle.default.*

/** Type-safe wrapper for interaction history JSON with its format.
  *
  * This provides a strongly-typed way to handle history serialization/deserialization
  * instead of passing raw JSON strings throughout the sync pipeline.
  *
  * The case class derives ReadWriter for serialization, making it safe to pass
  * format instances across server boundaries (case objects are naturally serializable).
  */
case class InteractionHistoryJson[T, GA, SA](
    format: InteractionFormat[T, GA, SA],
    historyJson: String
) derives ReadWriter {

  /** Deserialize the history using the provided format's serializer.
    *
    * @return The typed InteractionVariableHistory
    * @throws ParsingException if the JSON doesn't match the expected format
    */
  def toHistory: InteractionVariableHistory[T, GA, SA] =
    ParsingContext("interactionVariableHistory", historyJson) {
      format.historySerializer.deserialize(historyJson)
    }

  /** Deserialize the history payload (without fullHistory wrapper).
    *
    * @return The typed InteractionVariableHistory
    */
  def toHistoryPayload: InteractionVariableHistory[T, GA, SA] =
    ParsingContext("interactionVariableHistory payload", historyJson) {
      val value = ujson.read(historyJson)
      val history = value.obj.getOrElse("fullHistory", value)
      require(history.obj.keySet.toSet == Set("events"), "Expected event-based interaction history")
      format.historySerializer.deserialize(ujson.write(history))
    }

  /** Get the last timestamp from the history without full deserialization.
    *
    * Returns the timestamp as a string to preserve exact precision (nanoseconds).
    * While `LocalDateTime` supports nanosecond precision (9 fractional digits),
    * using the exact string ensures events are identical after serialization round-trips.
    */
  def lastTimestampString: Option[String] = {
    val value = ujson.read(historyJson)
    val history = value.obj.getOrElse("fullHistory", value)
    history.obj.get("events").flatMap { events =>
      events.obj.get("values").flatMap { valuesArr =>
        valuesArr.arr.lastOption.map(_.obj.get("timestamp").map(_.str))
      }
    }.flatten
  }

  /** Get the last timestamp from the history without full deserialization.
    *
    * `LocalDateTime` supports nanosecond precision (9 fractional digits), which
    * is sufficient for all use cases. This method parses the timestamp string,
    * and precision is preserved since the format supports at least as much
    * precision as `LocalDateTime`.
    */
  def lastTimestamp: Option[LocalDateTime] = lastTimestampString.map(LocalDateTime.parse)

  /** Serialize a history value using this format's serializer. */
  def serializeHistory(history: InteractionVariableHistory[T, GA, SA]): String =
    format.historySerializer.serialize(history)

  /** Merge multiple histories using this format's serializers. */
  def mergeWith(others: InteractionHistoryJson[T, GA, SA]*): String = {
    val histories = (this +: others).map(_.toHistory)
    format.historySerializer.serialize(histories.foldLeft(InteractionVariableHistory.empty[T, GA, SA])(_.mergeWith(_)))
  }

  /** Select events within a time range using this format's deserializer. */
  def select(strategy: EventSyncStrategy, starting: LocalDateTime, until: LocalDateTime): String = {
    require(!until.isBefore(starting), "History range end must not precede its start")
    val history = toHistory.cleanWithStrategy(strategy)
    format.historySerializer.serialize(history.copy(events = history.events.copy(values = history.events.values.filter(event =>
      !event.timestamp.isBefore(starting) && !event.timestamp.isAfter(until)))))
  }
}

object InteractionHistoryJson {

  /** Create an InteractionHistoryJson from typed history using the provided format. */
  def fromHistory[T, GA, SA](format: InteractionFormat[T, GA, SA], history: InteractionVariableHistory[T, GA, SA]): InteractionHistoryJson[T, GA, SA] =
    InteractionHistoryJson(format, format.historySerializer.serialize(history))

  /** Create an InteractionHistoryJson from a history JSON string and format. */
  def apply[T, GA, SA](format: InteractionFormat[T, GA, SA], historyJson: String): InteractionHistoryJson[T, GA, SA] =
    InteractionHistoryJson(format, historyJson)

  /** Store format with the history for database persistence. */
  def stored[T, GA, SA](key: String, history: InteractionHistoryJson[T, GA, SA]): String =
    ujson.write(ujson.Obj("keyForSerialisation" -> key, "fullHistory" -> ujson.read(history.historyJson)))

  /** Select events from a stored history JSON string using the provided format. */
  def select[T, GA, SA](format: InteractionFormat[T, GA, SA], json: String, strategy: EventSyncStrategy, starting: LocalDateTime, until: LocalDateTime): String = {
    val historyJson = InteractionHistoryJson(format, json)
    historyJson.select(strategy, starting, until)
  }
}
