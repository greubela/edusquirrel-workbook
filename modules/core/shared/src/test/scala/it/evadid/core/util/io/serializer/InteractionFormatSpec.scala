package it.evadid.core.util.io.serializer

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.interactions.gpt.{TextInputLlmGrader, TextInputWithScaffoldingAndGrading}
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.history.InteractionHistory
import it.evadid.workbook.history.events.*
import java.time.Instant
import munit.FunSuite
import upickle.default.*

class InteractionFormatSpec extends FunSuite {
  private val time = java.time.LocalDateTime.of(2026, 11, 17, 12, 1, 2, 123456789).toInstant(java.time.ZoneOffset.UTC)

  test("every known format has a unique stable name and roundtrips without a workbook") {
    assertEquals(knownFormats.map(_.name).distinct.size, knownFormats.size)
    knownFormats.foreach { format =>
      assertEquals(write[InteractionFormat](format), s"\"${format.name}\"")
      assert(read[InteractionFormat](write[InteractionFormat](format)) eq format)
      val empty = InteractionHistory.empty[format.type](format)
      assertEquals(format.historySerializer.deserialize(format.historySerializer.serialize(empty)), empty)
    }
  }

  test("format codecs preserve their concrete state types") {
    assertEquals(TextInteractionFormat.stateSerializer.deserialize(TextInteractionFormat.stateSerializer.serialize("hello")), "hello")
    assertEquals(LabeledCheckboxInteractionFormat.stateSerializer.deserialize(LabeledCheckboxInteractionFormat.stateSerializer.serialize(true)), true)
  }

  test("range bounds are inclusive and distinguish adjacent nanoseconds") {
    val format = TextInteractionFormat
    val history = InteractionHistory.empty(format)
      .addEvent(AnswerChangedEvent[format.type]("before", time.minusNanos(1), UpdateImportance.MAJOR))
      .addEvent(AnswerChangedEvent[format.type]("at", time, UpdateImportance.MAJOR))
      .addEvent(AnswerChangedEvent[format.type]("after", time.plusNanos(1), UpdateImportance.MAJOR))
    val bounded = InteractionHistoryJson.fromHistory(format, history).between(time, time).typedAs(format)
    assertEquals(bounded.answerEvents.map(_.newState), Set("at"))
  }

  test("different events at the same timestamp survive idempotent envelope merges") {
    val format = TextInteractionFormat
    val first = InteractionHistory.empty(format).addEvent(AnswerChangedEvent[format.type]("a", time, UpdateImportance.MAJOR))
    val second = InteractionHistory.empty(format).addEvent(AnswerChangedEvent[format.type]("b", time, UpdateImportance.MAJOR))
    val a = InteractionHistoryJson.fromHistory(format, first)
    val b = InteractionHistoryJson.fromHistory(format, second)
    assertEquals(a.merge(b).merge(a).typedAs(format), first.mergeWith(second))
  }

  test("unknown format names, old schemas and incompatible formats fail immediately") {
    intercept[Exception](read[InteractionFormat]("\"unknown-format\""))
    intercept[Exception](TextInteractionFormat.historySerializer.deserialize("{\"historyStates\":{\"events\":[]}}"))
    val envelope = InteractionHistoryJson.fromHistory(TextInteractionFormat, InteractionHistory.empty(TextInteractionFormat))
    intercept[IllegalArgumentException](envelope.typedAs(LabeledCheckboxInteractionFormat))
    intercept[Exception](InteractionHistoryJson.deserialize("{\"format\":\"unknown-format\",\"historyJson\":\"{}\"}"))
    intercept[Exception](InteractionHistoryJson(envelope.format, "invalid").toTyped)
    intercept[IllegalArgumentException](envelope.between(time.plusNanos(1), time))
  }
  test("history codecs reject answers of another format's state type") {
    val valid = InteractionHistory.empty(TextInteractionFormat)
      .addEvent(AnswerChangedEvent[TextInteractionFormat.type]("answer", time, UpdateImportance.MAJOR))
    val encoded = InteractionHistoryJson.fromHistory(TextInteractionFormat, valid)
    intercept[Exception](encoded.copy(format = LabeledCheckboxInteractionFormat).toTyped)
  }

}
