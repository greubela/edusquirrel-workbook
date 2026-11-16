package it.evadid.workbook.interaction.sync.model

import it.evadid.core.util.io.ParsingContext
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.InteractionFormat
import it.evadid.workbook.interaction.sync.EventSyncStrategy
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import java.time.LocalDateTime
import upickle.default.*

/** Strongly typed wrapper for interaction history JSON with its format.
  *
  * This provides a strongly-typed way to handle history serialization/deserialization
  * instead of passing raw JSON strings throughout the sync pipeline.
  *
  * Type parameters:
  * - T: The interaction state type (e.g., String, ProgrammingState)
  * - GA: The grading result type (typically Unit for no grading)
  * - SA: The scaffolding result type (typically Unit for no scaffolding)
  *
  * The format field contains the InteractionFormat case object which provides
  * type-safe serializers for the interaction. Since InteractionFormat derives
  * ReadWriter, it is automatically serializable (stores class name string).
  *
  * The format is passed explicitly and is necessary for the receiving server to
  * know how to deserialize the history JSON (e.g., for custom workbooks with
  * unknown interaction formats).
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
  def toTyped: InteractionVariableHistory[T, GA, SA] =
    ParsingContext("interactionVariableHistory", historyJson) {
      format.historySerializer.deserialize(historyJson)
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

  /** Select events within a time range using this format's deserializer. */
  def select(strategy: EventSyncStrategy, starting: LocalDateTime, until: LocalDateTime): String = {
    require(!until.isBefore(starting), "History range end must not precede its start")
    val history = toTyped.cleanWithStrategy(strategy)
    format.historySerializer.serialize(history.copy(events = history.events.copy(values = history.events.values.filter(event =>
      !event.timestamp.isBefore(starting) && !event.timestamp.isAfter(until)))))
  }

  /** Get the payload JSON value without full deserialization. */
  def payload: ujson.Value = {
    val value = ujson.read(historyJson)
    value.obj.getOrElse("fullHistory", value)
  }

  /** Get entries from a map in the history JSON without full deserialization. */
  def entries(key: String): List[(ujson.Value, ujson.Value)] = {
    val history = payload
    history.obj.get(key).toList.flatMap { mapValue =>
      mapValue.obj.map { fields =>
        fields.toList.map { case (k, v) => (ujson.read(k), v) }
      }
    }.flatten
  }

  /** Get entries from a nested map in the history JSON without full deserialization. */
  def entries(parentKey: String, childKey: String): List[(ujson.Value, ujson.Value)] = {
    val history = payload
    history.obj.get(parentKey).flatMap(_.obj).toList.flatMap { parent =>
      parent.get(childKey).toList.flatMap { mapValue =>
        mapValue.obj.map { fields =>
          fields.toList.map { case (k, v) => (ujson.read(k), v) }
        }
      }
    }.flatten
  }
}

object InteractionHistoryJson {

  /** ReadWriter for InteractionHistoryJson that stores format by class name. */
  implicit def interactionHistoryJsonReadWriter[T, GA, SA]: ReadWriter[InteractionHistoryJson[T, GA, SA]] = {
    import upickle.default.{ReadWriter => RW}
    RW.readwriter[ujson.Value].bimap[InteractionHistoryJson[T, GA, SA]](
      hist => ujson.Obj(
        "formatClassName" -> ujson.Str(hist.format.getClass.getName),
        "historyJson" -> ujson.Str(hist.historyJson)
      ),
      value => {
        val formatClassName = value.obj("formatClassName").str
        val historyJson = value.obj("historyJson").str
        val format = InteractionFormat.fromClassName(formatClassName)
        InteractionHistoryJson(format, historyJson)
      }
    )
  }

  /** Create an InteractionHistoryJson from typed history using the provided format. */
  def fromHistory[T, GA, SA](format: InteractionFormat[T, GA, SA], history: InteractionVariableHistory[T, GA, SA]): InteractionHistoryJson[T, GA, SA] =
    InteractionHistoryJson(format, format.historySerializer.serialize(history))

  /** Create an InteractionHistoryJson from a history JSON string and format. */
  def apply[T, GA, SA](format: InteractionFormat[T, GA, SA], historyJson: String): InteractionHistoryJson[T, GA, SA] =
    InteractionHistoryJson(format, historyJson)

  /** Merge multiple JSON strings using the provided format. */
  def merge[T, GA, SA](format: InteractionFormat[T, GA, SA], jsons: List[String]): String = {
    val histories = jsons.map(json => InteractionHistoryJson(format, json).toTyped)
    val merged = histories.foldLeft(InteractionVariableHistory.empty[T, GA, SA])(_.mergeWith(_))
    format.historySerializer.serialize(merged)
  }

  /** Store format with the history for database persistence. */
  def stored[T, GA, SA](key: String, history: InteractionHistoryJson[T, GA, SA]): String =
    ujson.write(ujson.Obj("keyForSerialisation" -> key, "fullHistory" -> ujson.read(history.historyJson)))

  /** Select events from a stored history JSON string using the provided format. */
  def select[T, GA, SA](format: InteractionFormat[T, GA, SA], json: String, strategy: EventSyncStrategy, starting: LocalDateTime, until: LocalDateTime): String = {
    val historyJson = InteractionHistoryJson(format, json)
    historyJson.select(strategy, starting, until)
  }
}
