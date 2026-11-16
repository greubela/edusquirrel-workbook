package it.evadid.workbook.interaction.sync.model

import it.evadid.core.util.io.ParsingContext
import it.evadid.workbook.interaction.sync.EventSyncStrategy
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
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
  def decode(json: String): InteractionVariableHistory[ujson.Value, ujson.Value, ujson.Value] =
    ParsingContext("interactionVariableHistory", json) { read[InteractionVariableHistory[ujson.Value, ujson.Value, ujson.Value]](payload(json)) }

  def lastTimestamp(json: String): Option[LocalDateTime] = decode(json).timestampLastChanged

  def stored(key: String, history: String): String =
    ujson.write(ujson.Obj("keyForSerialisation" -> key, "fullHistory" -> payload(history)))

  def merge(histories: List[String]): String = write(histories.foldLeft(InteractionVariableHistory.empty[ujson.Value, ujson.Value, ujson.Value])(
    (current, json) => current.mergeWith(decode(json))))

  def select(json: String, strategy: EventSyncStrategy, starting: LocalDateTime, until: LocalDateTime): String = {
    require(!until.isBefore(starting), "History range end must not precede its start")
    val history = decode(json).cleanWithStrategy(strategy)
    write(history.copy(events = history.events.copy(values = history.events.values.filter(event =>
      !event.timestamp.isBefore(starting) && !event.timestamp.isAfter(until)))))
  }
}
