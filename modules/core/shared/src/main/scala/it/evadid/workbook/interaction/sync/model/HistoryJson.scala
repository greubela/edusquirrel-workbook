package it.evadid.workbook.interaction.sync.model

import java.time.LocalDateTime

/** Transports inspect history metadata without knowing the saved answer or feedback types. */
object HistoryJson {
  def payload(json: String): ujson.Value = unwrap(ujson.read(json))

  private def unwrap(value: ujson.Value): ujson.Value = value.obj.get("fullHistory") match {
    case Some(ujson.Str(nested)) => payload(nested)
    case Some(nested) => unwrap(nested)
    case None => value
  }

  private def entries(value: ujson.Value): List[(ujson.Value, ujson.Value)] = value match {
    case obj: ujson.Obj => obj.value.toList.map((key, item) => ujson.Str(key) -> item)
    case arr: ujson.Arr => arr.value.toList.map(pair => pair(0) -> pair(1))
    case _ => throw IllegalArgumentException("Invalid history map.")
  }

  private def asMap(values: Iterable[(ujson.Value, ujson.Value)]): ujson.Value =
    ujson.Arr.from(values.map((key, item) => ujson.Arr(key, item)))

  /** Accept saved flat histories and envelopes; always write the separated history format. */
  def normalized(input: ujson.Value): ujson.Value = {
    val value = unwrap(input)
    val events = value.obj.get("historyStates").map(_("events")).orElse(value.obj.get("events")).getOrElse(ujson.Arr())
    val grades = value.obj.get("historyGrading").map(_("gradingsMap")).orElse(value.obj.get("gradings")).getOrElse(ujson.Arr())
    val hints = value.obj.get("historyScaffolding").map(_("scaffoldingMap")).orElse(value.obj.get("scaffoldings")).getOrElse(ujson.Arr())
    val result = ujson.Obj(
      "historyStates" -> ujson.Obj("events" -> events),
      "historyGrading" -> ujson.Obj("gradingsMap" -> asMap(entries(grades).map { (state, group) =>
        val results = group.obj.get("graderResultMap").getOrElse(asMap(group.obj.get("testsWithResults").toList.flatMap(_.arr).map(result => result("grader") -> result)))
        val answerTime = events.arr.filter(_("value") == state).flatMap(_.obj.get("timestamp"))
          .map(value => LocalDateTime.parse(value.str)).maxOption.getOrElse(LocalDateTime.MIN)
        val normalizedResults = entries(results).map { (grader, result) =>
          val fields = result.obj.toMap
          val status = fields("gradingStatus") match {
            case obj: ujson.Obj => ujson.Obj.from(obj.value.toMap.updated("$type", ujson.Str(obj("$type").str.replace("NotExecuted", "NotRequested"))))
            case other => other
          }
          grader -> ujson.Obj.from(fields.updated("gradingStatus", status)
            .updated("gradingTime", fields.getOrElse("gradingTime", ujson.Str(answerTime.toString))))
        }
        state -> ujson.Obj("state" -> group("state"), "graderResultMap" -> asMap(normalizedResults))
      })),
      "historyScaffolding" -> ujson.Obj("scaffoldingMap" -> asMap(entries(hints).map { (state, hint) =>
        val fields = hint.obj.toMap
        val status = fields.get("scaffoldingStatus").getOrElse(ujson.Obj(
          "$type" -> "ScaffoldingFeedback", "feedback" -> fields("scaffoldingResult")))
        state -> ujson.Obj.from((fields - "scaffoldingResult") + ("scaffoldingStatus" -> status))
      })))
    value.obj.get("states").foreach(result("states") = _)
    result
  }

  def lastTimestamp(json: String): Option[LocalDateTime] = {
    val value = normalized(payload(json))
    val edits = value("historyStates")("events").arr.toList ++ value.obj.get("states").toList.flatMap(_.arr)
    val grades = entries(value("historyGrading")("gradingsMap")).flatMap((_, group) => entries(group("graderResultMap")).map(_._2))
    val hints = entries(value("historyScaffolding")("scaffoldingMap")).map(_._2)
    (edits.flatMap(_.obj.get("timestamp")) ++ grades.flatMap(_.obj.get("gradingTime")) ++ hints.flatMap(_.obj.get("scaffoldingTime")))
      .map(value => LocalDateTime.parse(value.str)).maxOption
  }

  def stored(key: String, history: String): String =
    ujson.write(ujson.Obj("keyForSerialisation" -> key, "fullHistory" -> payload(history)))

  def merge(histories: List[String]): String = {
    val values = histories.map(history => normalized(payload(history)))
    val events = values.flatMap(_("historyStates")("events").arr).distinct
    val result = normalized(ujson.Obj("events" -> ujson.Arr.from(events)))
    val legacyStates = values.flatMap(_.obj.get("states").toList.flatMap(_.arr)).distinct
    if legacyStates.nonEmpty then result("states") = ujson.Arr.from(legacyStates)
    for (section, field) <- List("historyGrading" -> "gradingsMap", "historyScaffolding" -> "scaffoldingMap") do
      val merged = values.flatMap(value => entries(value(section)(field))).groupBy(_._1).values.map(_.head).toList
      result(section)(field) = asMap(merged)
    ujson.write(result)
  }
}
