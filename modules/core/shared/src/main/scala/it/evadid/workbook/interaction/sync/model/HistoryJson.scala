package it.evadid.workbook.interaction.sync.model

import java.time.LocalDateTime

/** Storage transports JSON without knowing the interaction's state or feedback types. */
object HistoryJson {
  def payload(json: String): ujson.Value = {
    val value = ujson.read(json)
    value.obj.get("fullHistory") match {
      case Some(ujson.Str(nested)) => payload(nested)
      case Some(nested) => nested
      case None => value
    }
  }

  def lastTimestamp(json: String): Option[LocalDateTime] = {
    val value = payload(json)
    List("events", "states").flatMap(value.obj.get)
      .flatMap(_.arr).map(event => LocalDateTime.parse(event("timestamp").str)).maxOption
  }

  def stored(key: String, history: String): String =
    ujson.write(ujson.Obj("keyForSerialisation" -> key, "fullHistory" -> payload(history)))

  def merge(histories: List[String]): String = {
    val values = histories.map(payload)
    val events = values.flatMap(v => v.obj.get("events").toList.flatMap(_.arr)).distinct
    val result = ujson.Obj("events" -> ujson.Arr.from(events))
    val legacyStates = values.flatMap(_.obj.get("states").toList.flatMap(_.arr)).distinct
    if (legacyStates.nonEmpty) result("states") = ujson.Arr.from(legacyStates)
    for (field <- List("gradings", "scaffoldings")) {
      val entries = values.flatMap(_.obj.get(field).toList.flatMap {
        case obj: ujson.Obj => obj.value.toList.map { case (key, value) => ujson.Arr(ujson.Str(key), value) }
        case arr: ujson.Arr => arr.value.toList
        case other => throw new IllegalArgumentException(s"Invalid history map: $other")
      }).groupBy(_(0)).values.map(_.head).toList
      result(field) = ujson.Arr.from(entries)
    }
    ujson.write(result)
  }
}
