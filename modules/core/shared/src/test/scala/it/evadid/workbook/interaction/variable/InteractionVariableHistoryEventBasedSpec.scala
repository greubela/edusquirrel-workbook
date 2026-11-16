package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.elements.interactionElements.gpt.TextInputLlmGrader
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, UpdateImportance}
import it.evadid.workbook.interaction.variable.events.*
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

class InteractionVariableHistoryEventBasedSpec extends FunSuite {
  private type Event = InteractionEvent[String, MessengerModel, String]
  private type History = InteractionVariableHistoryEventBased[String, MessengerModel, String]
  private val time = LocalDateTime.of(2026, 11, 16, 12, 0, 0, 123000000)
  private val grader = TextInputLlmGrader(LanguageMapContentId("test/task"), None,
    ContentForLlmInteraction(LanguageMapContentId("test/criterion"), Map.empty))
  private val otherGrader = grader.copy(gradeData = ContentForLlmInteraction(LanguageMapContentId("test/other"), Map.empty))

  private def answer(value: String, seconds: Long, importance: UpdateImportance = UpdateImportance.MAJOR): Event =
    AnswerChangedEvent(value, time.plusSeconds(seconds), importance)
  private def started(value: String, seconds: Long): Event =
    GradingStartedEvent(value, time.plusSeconds(seconds), grader)
  private def grade(value: String, seconds: Long, status: GradingStatus = GradingStatus.Correct()): GradingCompletedEvent[String, MessengerModel, String] =
    GradingCompletedEvent(GradingResult(grader, value, status, None, Some(MessengerModel.empty), time.plusSeconds(seconds)))
  private def hint(value: String, seconds: Long): Event = ScaffoldingCompletedEvent(
    ScaffoldingResult(value, ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("Try again")),
      FeedbackEntity.LlmEntity(), Some("additional scaffolding data"), time.plusSeconds(seconds)))
  private def history(events: Event*): History = InteractionVariableHistoryEventBased(InteractionEvents(events.toSet))

  test("empty histories expose empty views and project unrequested configured graders without inventing events") {
    val empty = history()
    assertEquals(empty.allStates, Set.empty)
    assertEquals(empty.gradingEvents, Set.empty)
    assertEquals(empty.scaffoldingEvents, Set.empty)
    assertEquals(empty.events.timestampLastChanged, None)
    val graded = empty.gradingForState(Seq(grader, otherGrader), "answer")
    assertEquals(graded.graderResultMap.keySet, Set[WorkbookInteractionGrader[String, MessengerModel]](grader, otherGrader))
    assert(graded.testsWithResults.forall(_.gradingStatus == GradingStatus.NotRequested()))
    assert(graded.testsWithResults.forall(_.gradingTime == LocalDateTime.MIN))
    assertEquals(empty.events.values, Set.empty)
    assertEquals(empty.clean(), empty)
    assertEquals(empty.cleanWithStrategy(EventSyncStrategy.KEEP_LAST_MAJOR), empty)
  }

  test("event views partition answers, grading lifecycle and scaffolding lifecycle") {
    val changed = answer("answer", 0)
    val gradingStart = started("answer", 1)
    val completed = grade("answer", 2)
    val scaffoldingStart: Event = ScaffoldingStartedEvent("answer", time.plusSeconds(3), FeedbackEntity.LlmEntity())
    val feedback = hint("answer", 4)
    val stream = history(changed, gradingStart, completed, scaffoldingStart, feedback)
    assertEquals(stream.allStates.map(_.newState), Set("answer"))
    assertEquals(stream.gradingEvents.toSet[Event], Set(gradingStart, completed))
    assertEquals(stream.scaffoldingEvents.toSet[Event], Set(scaffoldingStart, feedback))
    assertEquals(stream.events.timestampLastChanged, Some(time.plusSeconds(4)))
    assertEquals(completed.timestamp, completed.result.gradingTime)
    assertEquals(completed.associatedState, completed.result.gradedState)
  }

  test("grading projections use the requested state and configured grader identities") {
    val stream = history(grade("wanted", 2), grade("other state", 20),
      GradingCompletedEvent(GradingResult(otherGrader, "wanted", GradingStatus.Incorrect(), None, None, time.plusSeconds(3))))
    val equivalentGrader = grader.copy()
    val projected = stream.gradingForState(Seq(equivalentGrader, grader), "wanted")
    assertEquals(projected.graderResultMap.size, 1)
    val result = projected.graderResultMap(equivalentGrader)
    assertEquals(result.gradedState, "wanted")
    assertEquals(result.gradingTime, time.plusSeconds(2))
    assertEquals(result.gradingStatus, GradingStatus.Correct())
    assertEquals(result.grader, equivalentGrader)
    assertEquals(stream.gradingForState(Seq(grader), "absent").totalEvaluation(), GradingStatus.NotRequested())
  }

  test("grading is matched by its stable key rather than grader instance equality") {
    val configured = new WorkbookInteractionGrader[String, MessengerModel] {
      override val key = grader.key
      override val importance = grader.importance
      override val runningBehavior = grader.runningBehavior
      override val gradingEntity = grader.gradingEntity
      override protected def handleGrading(state: String, context: it.evadid.workbook.meta.ExecutionContextInformation)
          : scala.concurrent.Future[GradingResult[String, MessengerModel]] =
        throw AssertionError("Projecting history must not execute a grader")
    }
    assert(configured != grader)
    val completed = grade("answer", 2)
    val projected = history(completed).gradingForState(Seq(configured, grader), "answer")
    assertEquals(projected.graderResultMap.size, 1)
    val result = projected.graderResultMap(configured)
    assert(result.grader eq configured)
    assertEquals(result.gradingStatus, GradingStatus.Correct())
    assertEquals(result.gradingTime, completed.timestamp)
  }

  test("a newer start indicates an active retry; completion wins a start at the same timestamp") {
    val completed = grade("answer", 2)
    val retry = history(completed, started("answer", 3))
    val running = retry.gradingForState(Seq(grader), "answer").graderResultMap(grader)
    assertEquals(running.gradingStatus, GradingStatus.NotFinished())
    assertEquals(running.gradingTime, time.plusSeconds(3))
    val tied = history(started("answer", 2), completed)
    assertEquals(tied.gradingForState(Seq(grader), "answer").graderResultMap(grader), completed.result)
    val later = history(completed, grade("answer", 4, GradingStatus.Incorrect()))
    assertEquals(later.gradingForState(Seq(grader), "answer").totalEvaluation(), GradingStatus.Incorrect())
  }

  test("same-time completed grading results reuse the existing feedback merge rules") {
    val first = grade("answer", 2).result.copy(feedbackInformation = Some(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Gut"))))
    val second = first.copy(feedbackInformation = Some(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "Good"))))
    val stream = history(GradingCompletedEvent(first), GradingCompletedEvent(second))
    val result = stream.gradingForState(Seq(grader), "answer").graderResultMap(grader)
    assertEquals(result.feedbackInformation.get.tryGetInLanguage(German), Some("Gut"))
    assertEquals(result.feedbackInformation.get.tryGetInLanguage(English), Some("Good"))
    assertEquals(stream.gradingEvents.size, 2)
  }

  test("completed error events remain ordinary timestamped events") {
    val error = SerializedException("execution failed")
    val failed = grade("answer", 3, GradingStatus.TestError(error))
    val scaffoldingError: Event = ScaffoldingCompletedEvent(ScaffoldingResult("answer", ScaffoldingStatus.ScaffoldingError(error),
      FeedbackEntity.LlmEntity(), None, time.plusSeconds(4)))
    val stream = history(failed, scaffoldingError)
    assert(stream.gradingForState(Seq(grader), "answer").totalEvaluation().isError)
    assertEquals(stream.clean(), stream)
    assertEquals(stream.events.timestampLastChanged, Some(time.plusSeconds(4)))
  }

  test("completion events reject placeholder and still-running results, including when decoded") {
    val completed = grade("answer", 1)
    List(GradingStatus.NotRequested(), GradingStatus.NotFinished()).foreach { status =>
      intercept[IllegalArgumentException](GradingCompletedEvent[String, MessengerModel, String](completed.result.copy(gradingStatus = status)))
    }
    val incomplete = ScaffoldingResult[String, String]("answer", ScaffoldingStatus.ScaffoldingRequestedButNotFinished(), FeedbackEntity.LlmEntity(), None, time)
    intercept[IllegalArgumentException](ScaffoldingCompletedEvent[String, MessengerModel, String](incomplete))
    val json = writeJs[Event](completed)
    json("result")("gradingStatus") = writeJs[GradingStatus](GradingStatus.NotFinished())
    intercept[Exception](read[Event](json))
  }

  test("clean removes all temporary event kinds and preserves every other importance level") {
    val default = answer("default", 0, UpdateImportance.DEFAULT)
    val minor = answer("minor", 1, UpdateImportance.MINOR)
    val major = grade("answer", 3)
    val temporaryGrade: GradingCompletedEvent[String, MessengerModel, String] = major.copy(updateImportance = UpdateImportance.TEMPORARY)
    val temporaryHint = ScaffoldingCompletedEvent[String, MessengerModel, String](
      ScaffoldingResult("answer", ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap("Hint")), FeedbackEntity.TestEntity(), None, time),
      UpdateImportance.TEMPORARY)
    val stream = history(default, minor, major, answer("typing", 2, UpdateImportance.TEMPORARY),
      started("answer", 2), ScaffoldingStartedEvent("answer", time, FeedbackEntity.LlmEntity()), temporaryGrade, temporaryHint)
    assertEquals(stream.clean().events.values, Set(default, minor, major))
    assertEquals(stream.clean().clean(), stream.clean())
    assertEquals(stream.cleanWithStrategy(EventSyncStrategy.KEEP_ALL), stream)
  }

  test("importance filtering applies to grading and scaffolding without requiring an included answer event") {
    val completed = grade("answer", 5)
    val feedback = hint("answer", 6)
    val stream = history(answer("answer", 0, UpdateImportance.MINOR), started("answer", 4), completed, feedback)
    val selected = stream.cleanWithStrategy(EventSyncStrategy.KeepImportance(Set(UpdateImportance.MAJOR)))
    assertEquals(selected.events.values, Set(completed, feedback))
    assertEquals(selected.allStates, Set.empty)
    assertEquals(selected.gradingForState(Seq(grader), "answer").totalEvaluation(), GradingStatus.Correct())
  }

  test("latest-major filtering selects by event time across types and retains all boundary ties") {
    val changed = answer("answer", 5)
    val completed = grade("answer", 5)
    val feedback = hint("answer", 5)
    val temporary = answer("typing", 10, UpdateImportance.TEMPORARY)
    val stream = history(answer("older", 0), changed, completed, feedback, temporary)
    assertEquals(stream.cleanWithStrategy(EventSyncStrategy.KEEP_LAST_MAJOR).events.values, Set(changed, completed, feedback))
    assertEquals(stream.cleanWithStrategy(EventSyncStrategy.KEEP_LAST).events.values, Set(temporary))
  }

  test("timestamp filtering supports inclusive replay and strictly newer deltas") {
    val changed = answer("answer", 5)
    val completed = grade("answer", 5)
    val feedback = hint("answer", 6)
    val stream = history(answer("older", 0), changed, completed, feedback)
    assertEquals(stream.cleanWithStrategy(EventSyncStrategy.Since(time.plusSeconds(5))).events.values, Set(changed, completed, feedback))
    assertEquals(stream.cleanWithStrategy(EventSyncStrategy.Since(time.plusSeconds(5), includeBoundary = false)).events.values, Set(feedback))
  }

  test("merging replayed deltas is idempotent and keeps distinct events at the same timestamp") {
    val first = history(answer("a", 1), grade("a", 1))
    val delta = history(hint("a", 1), answer("b", 1))
    val merged = first.events.merge(delta.events)
    assertEquals(merged.values.size, 4)
    assertEquals(merged.merge(delta.events), merged)
    assertEquals(delta.events.merge(first.events), merged)
    assertEquals(merged.timestampLastChanged, Some(time.plusSeconds(1)))
  }

  test("history mergeWith combines single or multiple histories without cleaning or changing the inputs") {
    val first = history(answer("first", 1), started("first", 1))
    val second = history(grade("first", 1), hint("first", 1))
    val third = history(answer("third", 2))
    val combined = first.mergeWith(Seq(second, third, second))
    assertEquals(combined.events.values, first.events.values ++ second.events.values ++ third.events.values)
    assertEquals(first.mergeWith(second), second.mergeWith(first))
    assertEquals(first.mergeWith(second).mergeWith(third), first.mergeWith(second.mergeWith(third)))
    assertEquals(combined.mergeWith(combined), combined)
    assertEquals(first.mergeWith(Seq.empty), first)
    assertEquals(first.events.values.size, 2)
    assert(combined.events.values.exists(_.updateImportance == UpdateImportance.TEMPORARY))
  }

  test("every event and family, full history and every strategy round-trip through JSON and MessagePack") {
    val stream = history(answer("Grüße", 0), started("Grüße", 1), grade("Grüße", 2),
      ScaffoldingStartedEvent("Grüße", time.plusSeconds(3), FeedbackEntity.LlmEntity()), hint("Grüße", 4))
    stream.events.values.foreach { event =>
      assertEquals(read[Event](write[Event](event)), event)
      assertEquals(readBinary[Event](writeBinary[Event](event)), event)
    }
    stream.gradingEvents.foreach { event =>
      assertEquals(read[GradingEvent[String, MessengerModel, String]](write(event)), event)
    }
    stream.scaffoldingEvents.foreach { event =>
      assertEquals(read[ScaffoldingEvent[String, MessengerModel, String]](write(event)), event)
    }
    assertEquals(read[History](write(stream)), stream)
    assertEquals(readBinary[History](writeBinary(stream)), stream)
    val strategies = List(EventSyncStrategy.KEEP_ALL, EventSyncStrategy.KEEP_LAST, EventSyncStrategy.KEEP_LAST_MAJOR,
      EventSyncStrategy.KeepImportance(Set(UpdateImportance.MINOR)), EventSyncStrategy.Since(time),
      EventSyncStrategy.Since(time, includeBoundary = false))
    strategies.foreach { strategy =>
      val restored = read[EventSyncStrategy](write[EventSyncStrategy](strategy))
      assertEquals(restored, strategy)
      assertEquals(readBinary[EventSyncStrategy](writeBinary[EventSyncStrategy](strategy)), strategy)
      assertEquals(stream.cleanWithStrategy(restored), stream.cleanWithStrategy(strategy))
    }
  }

  test("structured answer values and additional scaffolding data survive serialization") {
    type Structured = InteractionVariableHistoryEventBased[List[String], Int, List[Int]]
    val value = List("Grüße", "second")
    val answer: InteractionEvent[List[String], Int, List[Int]] = AnswerChangedEvent(value, time, UpdateImportance.MAJOR)
    val feedback: InteractionEvent[List[String], Int, List[Int]] = ScaffoldingCompletedEvent(
      ScaffoldingResult(value, ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap("Hint")), FeedbackEntity.TestEntity(), Some(List(1, 2, 3)), time))
    val stream: Structured = InteractionVariableHistoryEventBased(InteractionEvents(Set(answer, feedback)))
    assertEquals(read[Structured](write(stream)), stream)
    assertEquals(readBinary[Structured](writeBinary(stream)), stream)
  }
}
