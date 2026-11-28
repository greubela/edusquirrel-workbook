package it.evadid.workbook.sync.model

import it.evadid.core.datastructures.language.WorkbookContent

import it.evadid.core.datastructures.chat.{Message, MessengerModel}
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, LanguageMapContentId}
import it.evadid.distribution.command.SerializedException
import it.evadid.distribution.command.SerializedException.SimpleStackTraceElement
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.abstractions.InteractionFormat.{ChoiceInteractionFormat, LabeledCheckboxInteractionFormat,
  SqlCommandExerciseFormat, TextInputWithLlmGradingAndScaffoldingFormat, TextInteractionFormat}
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.interactions.choice.WorkbookContentChoiceInteraction.*
import it.evadid.workbook.interactions.gpt.{TextInputLlmGrader, TextInputWithScaffoldingAndGrading}
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.history.InteractionHistory
import it.evadid.workbook.history.events.*
import java.time.Instant
import it.evadid.workbook.abstractions.InteractionFormat
import munit.FunSuite
import upickle.default.*

/** The envelope must retain its own type information without any workbook or context registry. */
class InteractionHistoryJsonSpec extends FunSuite {
  private type FeedbackFormat = TextInputWithLlmGradingAndScaffoldingFormat.type
  private val feedbackFormat: FeedbackFormat = TextInputWithLlmGradingAndScaffoldingFormat
  private val time = java.time.LocalDateTime.of(2026, 11, 18, 12, 13, 14, 123456789).toInstant(java.time.ZoneOffset.UTC)
  private val answer = "quoted \"answer\"\\path\n学校 🧪"
  private val grader = TextInputLlmGrader(LanguageMapContentId("custom/task"), None,
    TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(LanguageMapContentId("custom/criterion"), Map.empty))
  private val transcript = MessengerModel(Seq(Message("feedback transcript\n学校", MessengerModel.pAgent)))
  private val feedback: LanguageMap[AppLanguage.HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Try again", AppLanguage.German -> "Noch einmal"))
  private val answerRef = InteractionFormat.referenceOf(feedbackFormat, answer)
  private val feedbackHistory = InteractionHistory[FeedbackFormat](feedbackFormat, InteractionEvents(Set(
    AnswerChangedEvent[FeedbackFormat](answer, time, UpdateImportance.MINOR),
    GradingStartedEvent[FeedbackFormat](answerRef, time.plusNanos(1), grader.reference),
    GradingCompletedEvent[FeedbackFormat](GradingResult(grader, answer, GradingStatus.Correct,
      Some(WorkbookContent(feedback)), Some(transcript), time.plusNanos(2)).record(answerRef)),
    ScaffoldingStartedEvent[FeedbackFormat](answerRef, time.plusNanos(3), FeedbackEntity.LlmEntity),
    ScaffoldingCompletedEvent[FeedbackFormat](ScaffoldingResult(answer, ScaffoldingStatus.ScaffoldingFeedback(feedback),
      FeedbackEntity.LlmEntity, Some(transcript), time.plusNanos(4)).record(answerRef))
  )))

  private def copies(value: InteractionHistoryJson): Seq[InteractionHistoryJson] = Seq(
    InteractionHistoryJson.deserialize(value.serialized),
    read[InteractionHistoryJson](write[InteractionHistoryJson](value)),
    readBinary[InteractionHistoryJson](writeBinary[InteractionHistoryJson](value)))

  private def assertRoundTrips[F <: InteractionFormat](format: F, history: InteractionHistory[F]): Unit = {
    val original = InteractionHistoryJson.fromHistory(format, history)
    copies(original).foreach { decoded =>
      assertEquals(decoded, original, format.name) // Includes the exact inner JSON string.
      assert(decoded.format eq format)
      assertEquals(decoded.typedAs(format), history)
      val selfTyped = decoded.toTyped
      assertEquals(InteractionHistoryJson.fromHistory[decoded.format.type](decoded.format, selfTyped).typedAs(format), history)
    }
  }

  test("empty envelopes round-trip through JSON and MessagePack for every registered format") {
    InteractionFormat.knownFormats.foreach { format =>
      assertRoundTrips[format.type](format, InteractionHistory.empty(format))
    }
  }

  test("answer envelopes preserve escaping, importance, timestamp ties and the full nanosecond range") {
    val events = Set[InteractionEvent[TextInteractionFormat.type]](
      AnswerChangedEvent[TextInteractionFormat.type]("minimum", Instant.MIN, UpdateImportance.DEFAULT),
      AnswerChangedEvent[TextInteractionFormat.type](answer, time, UpdateImportance.TEMPORARY),
      AnswerChangedEvent[TextInteractionFormat.type]("same timestamp", time, UpdateImportance.MINOR),
      AnswerChangedEvent[TextInteractionFormat.type]("adjacent nanosecond", time.plusNanos(1), UpdateImportance.MAJOR),
      AnswerChangedEvent[TextInteractionFormat.type]("maximum", Instant.MAX, UpdateImportance.MAJOR))
    assertRoundTrips(TextInteractionFormat, InteractionHistory(TextInteractionFormat, InteractionEvents(events)))
  }

  test("populated Boolean and structured answer envelopes use their declared state codecs") {
    val checkbox = InteractionHistory.empty(LabeledCheckboxInteractionFormat)
      .addEvent(AnswerChangedEvent[LabeledCheckboxInteractionFormat.type](true, time, UpdateImportance.MAJOR))
    val choice = InteractionHistory.empty(ChoiceInteractionFormat)
      .addEvent(AnswerChangedEvent[ChoiceInteractionFormat.type](ChoiceState(Seq(ChoiceSelectedEvent(time, ChoiceOptionWorkbookContent(WorkbookContent(LanguageMapContentId("custom/option")), None)))), time, UpdateImportance.MAJOR))
    assertRoundTrips(LabeledCheckboxInteractionFormat, checkbox)
    assertRoundTrips(ChoiceInteractionFormat, choice)
  }

  test("all feedback lifecycle events retain multilingual feedback, transcripts and timestamps") {
    assertEquals(feedbackHistory.size, 5)
    assertRoundTrips(TextInputWithLlmGradingAndScaffoldingFormat, feedbackHistory)
  }

  test("feedback-only deltas round-trip without inventing an answer event") {
    val delta = feedbackHistory.copy(events = InteractionEvents(feedbackHistory.events.values.filter {
      case _: AnswerChangedEvent[FeedbackFormat] => false
      case _ => true
    }))
    assert(delta.answerEvents.isEmpty)
    assertRoundTrips(TextInputWithLlmGradingAndScaffoldingFormat, delta)
  }

  test("saved feedback errors keep the messages of an error and its causes, never a stack trace") {
    val cause = SerializedException("root cause", Seq.empty, None)
    val error = SerializedException("execution failed", Seq(SimpleStackTraceElement("Grader", "run", "Grader.scala", 42)), Some(cause))
    val history = InteractionHistory[FeedbackFormat](feedbackFormat, InteractionEvents(Set(
      GradingCompletedEvent[FeedbackFormat](GradingResult(grader, answer, GradingStatus.TestError(error),
        Some(WorkbookContent(feedback)), Some(transcript), time).record(answerRef)),
      ScaffoldingCompletedEvent[FeedbackFormat](ScaffoldingResult(answer, ScaffoldingStatus.ScaffoldingError(error),
        FeedbackEntity.LlmEntity, Some(transcript), time.plusNanos(1)).record(answerRef))
    )))
    assertRoundTrips(TextInputWithLlmGradingAndScaffoldingFormat, history)
    assertEquals(GradingStatus.TestError(error), GradingStatus.TestError("execution failed: root cause"))
    assertEquals(ScaffoldingStatus.ScaffoldingError(error), ScaffoldingStatus.ScaffoldingError("execution failed: root cause"))
    val saved = InteractionHistoryJson.fromHistory(TextInputWithLlmGradingAndScaffoldingFormat, history).serialized
    assert(!saved.contains("Grader.scala") && !saved.contains("stackTrace"), saved)
  }

  test("the envelope nests the history as JSON; a history read as text is parsed once") {
    val envelope = InteractionHistoryJson.fromHistory(TextInputWithLlmGradingAndScaffoldingFormat, feedbackHistory)
    val json = ujson.read(envelope.serialized)
    assert(json("historyJson").objOpt.isDefined, envelope.serialized)
    assertEquals(InteractionHistoryJson(envelope.format, envelope.historyJson.render()), envelope)
    intercept[IllegalArgumentException](InteractionHistoryJson(envelope.format, "{not json"))
  }

  test("heterogeneous envelope sequences round-trip without a shared interaction type") {
    val text = InteractionHistory.empty(TextInteractionFormat)
      .addEvent(AnswerChangedEvent[TextInteractionFormat.type](answer, time, UpdateImportance.MAJOR))
    val checkbox = InteractionHistory.empty(LabeledCheckboxInteractionFormat)
      .addEvent(AnswerChangedEvent[LabeledCheckboxInteractionFormat.type](false, time, UpdateImportance.MAJOR))
    val original = Seq(InteractionHistoryJson.fromHistory(TextInteractionFormat, text),
      InteractionHistoryJson.fromHistory(LabeledCheckboxInteractionFormat, checkbox),
      InteractionHistoryJson.fromHistory(TextInputWithLlmGradingAndScaffoldingFormat, feedbackHistory))
    Seq(read[Seq[InteractionHistoryJson]](write(original)), readBinary[Seq[InteractionHistoryJson]](writeBinary(original))).foreach { decoded =>
      assertEquals(decoded, original)
      assertEquals(decoded(0).typedAs(TextInteractionFormat), text)
      assertEquals(decoded(1).typedAs(LabeledCheckboxInteractionFormat), checkbox)
      assertEquals(decoded(2).typedAs(TextInputWithLlmGradingAndScaffoldingFormat), feedbackHistory)
    }
  }

  test("format identity survives round trips even when two formats have identical payload schemas") {
    val eventId = EventId.random()
    val text = InteractionHistory.empty(TextInteractionFormat)
      .addEvent(AnswerChangedEvent[TextInteractionFormat.type](answer, time, UpdateImportance.MAJOR, eventId))
    val sql = InteractionHistory.empty(SqlCommandExerciseFormat)
      .addEvent(AnswerChangedEvent[SqlCommandExerciseFormat.type](answer, time, UpdateImportance.MAJOR, eventId))
    val textEnvelope = InteractionHistoryJson.fromHistory(TextInteractionFormat, text)
    val sqlEnvelope = InteractionHistoryJson.fromHistory(SqlCommandExerciseFormat, sql)
    assertEquals(textEnvelope.historyJson, sqlEnvelope.historyJson)
    assertRoundTrips(TextInteractionFormat, text)
    assertRoundTrips(SqlCommandExerciseFormat, sql)
    copies(textEnvelope).foreach(decoded => intercept[IllegalArgumentException](decoded.typedAs(SqlCommandExerciseFormat)))
  }

  test("validated deserialization rejects corrupt inner JSON and state data incompatible with its format") {
    val valid = InteractionHistoryJson.fromHistory(TextInputWithLlmGradingAndScaffoldingFormat, feedbackHistory)
    intercept[Exception](InteractionHistoryJson.deserialize(InteractionHistoryJson(valid.format, "not JSON").serialized))
    intercept[Exception](InteractionHistoryJson.deserialize(valid.copy(format = LabeledCheckboxInteractionFormat).serialized))
    intercept[Exception](InteractionHistoryJson.deserialize("""{"format":"unknown-format","historyJson":"{}"}"""))
  }
}
