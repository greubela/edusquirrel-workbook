package it.evadid.workbook.interaction.sync.model

import it.evadid.core.util.io.ParsingContext
import it.evadid.workbook.interaction.sync.EventSyncStrategy
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.InteractionEvents
import java.time.LocalDateTime
import upickle.default.*

/** Current event payload operations shared by browser storage and backend commands. No snapshot-format reader. */
object HistoryJson {
  def payload(json: String): ujson.Value = ParsingContext("interactionVariableHistory JSON", json) {
    val value = ujson.read(json)
    val history = value.obj.getOrElse("fullHistory", value)
    require(history.obj.keySet.toSet == Set("events"), "Expected event-based interaction history")
    history
  }
  def decode(json: String): InteractionVariableHistory[ujson.Value] =
    ParsingContext("interactionVariableHistory", json) { read[InteractionVariableHistory[ujson.Value]](payload(json)) }

  def lastTimestamp(json: String): Option[LocalDateTime] = decode(json).timestampLastChanged

  def stored(key: String, history: String): String =
    ujson.write(ujson.Obj("keyForSerialisation" -> key, "fullHistory" -> payload(history)))

  def merge(histories: List[String]): String = {
    val initial = InteractionVariableHistory.empty[ujson.Value]
    histories.foldLeft(initial) { (current, json) =>
      current.mergeWith(decode(json))
    }
  }

  /** Iterate over the (ujson key, ujson value) pairs of a JSON object; the key is parsed as JSON so complex keys match. */
  def entries(value: ujson.Value): Iterator[(ujson.Value, ujson.Value)] = value.obj.map { (k, v) =>
    ujson.read(k) -> v
  }.iterator

  def select(json: String, strategy: EventSyncStrategy, starting: LocalDateTime, until: LocalDateTime): String = {
    require(!until.isBefore(starting), "History range end must not precede its start")
    val history = decode(json).cleanWithStrategy(strategy)
    val filteredEvents = history.events.values.filter(event =>
      !event.timestamp.isBefore(starting) && !event.timestamp.isAfter(until))
    write(history.copy(events = InteractionEvents(filteredEvents)))
  }
}
