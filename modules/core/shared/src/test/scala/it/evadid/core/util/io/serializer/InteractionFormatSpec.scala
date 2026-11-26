package it.evadid.core.util.io.serializer

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.elements.interactionElements.gpt.{TextInputLlmGrader, TextInputWithScaffoldingAndGrading}
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.*
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

class InteractionFormatSpec extends FunSuite {
  private val time = LocalDateTime.of(2026, 11, 17, 12, 1, 2, 123456789)

  test("every known format has a unique stable name and roundtrips without a workbook") {
    assertEquals(knownFormats.map(_.name).distinct.size, knownFormats.size)
    knownFormats.foreach { format =>
      assertEquals(write[InteractionFormat](format), s"\"${format.name}\"")
      assert(read[InteractionFormat](write[InteractionFormat](format)) eq format)
      val empty = InteractionVariableHistory.empty[format.type]
      assertEquals(format.historySerializer.deserialize(format.historySerializer.serialize(empty)), empty)
    }
  }

  test("format codecs preserve their concrete state types") {
    assertEquals(TextInteractionFormat.stateSerializer.deserialize(TextInteractionFormat.stateSerializer.serialize("hello")), "hello")
    assertEquals(LabeledCheckboxInteractionFormat.stateSerializer.deserialize(LabeledCheckboxInteractionFormat.stateSerializer.serialize(true)), true)
  }

  test("range bounds are inclusive and distinguish adjacent nanoseconds") {
    val format = TextInteractionFormat
    val history = InteractionVariableHistory.empty[format.type]
      .addEvent(AnswerChangedEvent[format.type]("before", time.minusNanos(1), UpdateImportance.MAJOR))
      .addEvent(AnswerChangedEvent[format.type]("at", time, UpdateImportance.MAJOR))
      .addEvent(AnswerChangedEvent[format.type]("after", time.plusNanos(1), UpdateImportance.MAJOR))
    val bounded = InteractionHistoryJson.fromHistory(format, history).between(time, time).typedAs(format)
    assertEquals(bounded.events.values.map(_.associatedState), Set("at"))
  }

  test("different events at the same timestamp survive idempotent envelope merges") {
    val format = TextInteractionFormat
    val first = InteractionVariableHistory.empty[format.type].addEvent(AnswerChangedEvent[format.type]("a", time, UpdateImportance.MAJOR))
    val second = InteractionVariableHistory.empty[format.type].addEvent(AnswerChangedEvent[format.type]("b", time, UpdateImportance.MAJOR))
    val a = InteractionHistoryJson.fromHistory(format, first)
    val b = InteractionHistoryJson.fromHistory(format, second)
    assertEquals(a.merge(b).merge(a).typedAs(format), first.mergeWith(second))
  }

  test("unknown format names, old schemas and incompatible formats fail immediately") {
    intercept[Exception](read[InteractionFormat]("\"unknown-format\""))
    intercept[Exception](TextInteractionFormat.historySerializer.deserialize("{\"historyStates\":{\"events\":[]}}"))
    val envelope = InteractionHistoryJson.fromHistory(TextInteractionFormat, InteractionVariableHistory.empty[TextInteractionFormat.type])
    intercept[IllegalArgumentException](envelope.typedAs(LabeledCheckboxInteractionFormat))
    intercept[Exception](InteractionHistoryJson.deserialize("{\"format\":\"unknown-format\",\"historyJson\":\"{}\"}"))
    intercept[Exception](envelope.copy(historyJson = "invalid").toTyped)
    intercept[IllegalArgumentException](envelope.between(time.plusNanos(1), time))
  }
  test("history codecs reject incompatible graders") {
    val grader = TextInputLlmGrader(LanguageMapContentId("custom/task"), None,
      TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(LanguageMapContentId("custom/criterion"), Map.empty))
    val valid = InteractionVariableHistory.empty[TextInputWithLlmGradingAndScaffoldingFormat.type]
      .addEvent(GradingStartedEvent[TextInputWithLlmGradingAndScaffoldingFormat.type]("answer", time, grader))
    val encoded = InteractionHistoryJson.fromHistory(TextInputWithLlmGradingAndScaffoldingFormat, valid)
    intercept[Exception](encoded.copy(format = TextInteractionFormat).toTyped)
  }

}
