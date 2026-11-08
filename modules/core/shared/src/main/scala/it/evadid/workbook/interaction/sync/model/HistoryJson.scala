package it.evadid.workbook.interaction.sync.model

import java.time.LocalDateTime

/** Inspect the current history schema without knowing answer or feedback types. */
object HistoryJson {
  def payload(json: String): ujson.Value = {
    val value = ujson.read(json)
    val history = value.obj.getOrElse("fullHistory", value)
    history.obj
    history
  }

  private def entries(value: ujson.Value): List[(ujson.Value, ujson.Value)] = value match {
    case obj: ujson.Obj => obj.value.toList.map((key, item) => ujson.Str(key) -> item)
    case arr: ujson.Arr => arr.value.toList.map(pair => pair(0) -> pair(1))
    case _ => throw IllegalArgumentException("Invalid history map.")
  }

  private def asMap(values: Iterable[(ujson.Value, ujson.Value)]): ujson.Value =
    ujson.Arr.from(values.map((key, item) => ujson.Arr(key, item)))

  def lastTimestamp(json: String): Option[LocalDateTime] = {
    val value = payload(json)
    val edits = value("historyStates")("events").arr.toList
    val grades = entries(value("historyGrading")("gradingsMap")).flatMap((_, group) => entries(group("graderResultMap")).map(_._2))
    val hints = entries(value("historyScaffolding")("scaffoldingMap")).map(_._2)
    (edits.map(_("timestamp")) ++ grades.map(_("gradingTime")) ++ hints.map(_("scaffoldingTime")))
      .map(value => LocalDateTime.parse(value.str)).maxOption
  }

  def stored(key: String, history: String): String =
    ujson.write(ujson.Obj("keyForSerialisation" -> key, "fullHistory" -> payload(history)))

  def merge(histories: List[String]): String = {
    val values = histories.map(payload)
    val events = values.flatMap(_("historyStates")("events").arr).distinct
    val feedback = List("historyGrading" -> "gradingsMap", "historyScaffolding" -> "scaffoldingMap").map { (section, field) =>
      val merged = values.flatMap(value => entries(value(section)(field))).groupBy(_._1).values.map(_.head).toList
      section -> ujson.Obj(field -> asMap(merged))
    }
    ujson.write(ujson.Obj.from(("historyStates" -> ujson.Obj("events" -> ujson.Arr.from(events))) :: feedback))
  }
}
