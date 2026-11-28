package it.evadid.workbook.history

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.abstractions.InteractionFormat.TextInputWithLlmGradingAndScaffoldingFormat
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.interactions.gpt.TextInputLlmGrader
import it.evadid.workbook.interactions.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
import it.evadid.workbook.sync.{EventSyncStrategy, UpdateImportance}
import it.evadid.workbook.sync.UpdateImportance.*
import it.evadid.workbook.history.events.*
import java.time.Instant
import munit.FunSuite

/** The projections every editor, renderer and cache reads from an event history. */
class InteractionVariableHistorySpec extends FunSuite {
  private type F = TextInputWithLlmGradingAndScaffoldingFormat.type
  private val format: F = TextInputWithLlmGradingAndScaffoldingFormat
  private def ref(value: String) = InteractionFormat.referenceOf(format, value)
  private def refs(values: String*) = values.map(ref).toSet
  private type Event = InteractionEvent[F]
  private val at = java.time.LocalDateTime.of(2026, 5, 4, 10, 0).toInstant(java.time.ZoneOffset.UTC)
  private def t(second: Int) = at.plusSeconds(second)
  private def grader(task: String): WorkbookInteractionGrader[String, MessengerModel] =
    TextInputLlmGrader(LanguageMapContentId(s"spec/$task"), None, ContentForLlmInteraction(LanguageMapContentId("spec/criterion"), Map.empty))
  private val spelling = grader("spelling")
  private val content = grader("content")
  private val unused = grader("unused")
  private val tutor = FeedbackEntity.LlmEntity

  private def answer(value: String, second: Int, importance: UpdateImportance = MINOR): Event =
    AnswerChangedEvent[F](value, t(second), importance)
  private def graded(value: String, second: Int, by: WorkbookInteractionGrader[String, MessengerModel] = spelling, status: GradingStatus = GradingStatus.Correct) =
    GradingResult[String, MessengerModel](by, value, status, None, None, t(second))
  private def gradingDone(value: String, second: Int, by: WorkbookInteractionGrader[String, MessengerModel] = spelling, status: GradingStatus = GradingStatus.Correct): Event =
    GradingCompletedEvent[F](graded(value, second, by, status).record(ref(value)))
  private def gradingStarted(value: String, second: Int, by: WorkbookInteractionGrader[String, MessengerModel] = spelling): Event =
    GradingStartedEvent[F](ref(value), t(second), by.reference)
  private def hint(value: String, second: Int) =
    ScaffoldingResult[String, MessengerModel](value, ScaffoldingStatus.ScaffoldingFeedback(
      it.evadid.core.datastructures.language.LanguageMap.universalMap(s"hint for $value")), tutor, None, t(second))
  private def hintDone(value: String, second: Int): Event = ScaffoldingCompletedEvent[F](hint(value, second).record(ref(value)))
  private def hintStarted(value: String, second: Int): Event = ScaffoldingStartedEvent[F](ref(value), t(second), tutor)
  private def history(events: Event*) = InteractionVariableHistory[F](format, InteractionEvents(events.toSet))
  /** The answers `events` belong to. */
  private def values(events: Iterable[Event]) = events.map(history().stateOf).toSet

  test("cleaning drops the initial default answer and its feedback once the user has answered") {
    val start = answer("start", 0, DEFAULT)
    val typed = answer("typed", 2)
    val feedback = gradingDone("typed", 3)
    val normalized = history(start, gradingDone("start", 1), typed, feedback).cleanedHistory()
    assertEquals(normalized.events.values, Set(typed, feedback))
    assertEquals(values(normalized.events.values), refs("typed"))
    assertEquals(normalized.allStates.size, 1)
    assertEquals(normalized.gradingEvents.size, 1)
  }

  test("normalization keeps only the latest of several defaults and keeps feedback for values that are still answered") {
    val onlyDefaults = history(answer("old", 0, DEFAULT), answer("new", 1, DEFAULT), gradingDone("old", 2)).cleanedHistory()
    assertEquals(values(onlyDefaults.events.values), refs("new"))
    val repeated = history(answer("same", 0, DEFAULT), answer("same", 1), gradingDone("same", 2)).cleanedHistory()
    assertEquals(repeated.allStates.map(_.updateImportance), Set(MINOR))
    assertEquals(repeated.gradingEvents.size, 1, "the feedback still belongs to an answer in the history")
    assertEquals(history().cleanedHistory(), history())
  }

  test("the latest answer includes timestamp ties and exactly the feedback for those answers") {
    val events = Seq(answer("first", 1), gradingDone("first", 2), answer("tieA", 5), answer("tieB", 5),
      gradingDone("tieA", 6), hintDone("tieB", 7), hintStarted("first", 8))
    val latest = history(events*).latestAnswerEvents
    assertEquals(values(latest), refs("tieA", "tieB"))
    assertEquals(latest.size, 4)
    assertEquals(history().latestAnswerEvents, Set.empty[Event])
    assertEquals(history(answer("start", 0, DEFAULT), answer("mine", 1)).latestAnswerEvents.map(history().stateOf), refs("mine"))
  }

  test("grading projects the latest lifecycle event per configured grader") {
    val state = "answer"
    val restarted = history(answer(state, 0), gradingStarted(state, 1), gradingDone(state, 2),
      gradingDone(state, 1, content), gradingStarted(state, 3, content), gradingDone("other answer", 9))
    val projected = restarted.gradingForState(Seq(spelling, content, unused, spelling), state)
    assertEquals(projected.state, state)
    assertEquals(projected.graderResultMap.keySet, Set(spelling, content, unused), "known graders appear once")
    assertEquals(projected.graderResultMap(spelling).gradingStatus, GradingStatus.Correct)
    assertEquals(projected.graderResultMap(content).gradingStatus, GradingStatus.NotFinished, "a newer start supersedes an older result")
    assertEquals(projected.graderResultMap(content).gradingTime, t(3))
    assertEquals(projected.graderResultMap(unused).gradingStatus, GradingStatus.NotRequested)
    val tie = history(gradingStarted(state, 4), gradingDone(state, 4, status = GradingStatus.Incorrect))
    assertEquals(tie.gradingForState(Seq(spelling), state).graderResultMap(spelling).gradingStatus, GradingStatus.Incorrect,
      "completion wins a timestamp tie")
    assertEquals(restarted.gradingForState(Seq(spelling), "never graded").graderResultMap(spelling).gradingStatus, GradingStatus.NotRequested)
  }

  test("scaffolding projects the latest hint lifecycle for the state") {
    assertEquals(history(answer("a", 0)).scaffoldingForState("a"), None)
    val pending = history(hintStarted("a", 1)).scaffoldingForState("a")
    assertEquals(pending.map(_.scaffoldingStatus), Some(ScaffoldingStatus.ScaffoldingRequestedButNotFinished()))
    assertEquals(pending.map(_.scaffoldingTime), Some(t(1)))
    assertEquals(history(hintStarted("a", 1), hintDone("a", 2)).scaffoldingForState("a"), Some(hint("a", 2)))
    assertEquals(history(hintDone("a", 2), hintStarted("a", 3)).scaffoldingForState("a").map(_.scaffoldingStatus),
      Some(ScaffoldingStatus.ScaffoldingRequestedButNotFinished()), "a requested follow-up hint is pending again")
    assertEquals(history(hintDone("b", 2)).scaffoldingForState("a"), None)
  }

  test("adding results ignores unfinished gradings and never duplicates a published result") {
    val empty = InteractionVariableHistory.empty(format)
    assertEquals(empty.addGrading(graded("a", 1, status = GradingStatus.NotFinished)), empty)
    assertEquals(empty.addGrading(graded("a", 1, status = GradingStatus.NotRequested)), empty)
    val once = empty.addGrading(graded("a", 1))
    assertEquals(once.addGrading(graded("a", 1)), once)
    assertEquals(once.addGradings(Seq(graded("a", 1), graded("a", 2))).gradingEvents.size, 2)
    val both = empty.addGrading(GradedState("a", Map(spelling -> graded("a", 1), content -> graded("a", 1, content))))
    assertEquals(both.gradingEvents.map(_.grader), Set(spelling.reference, content.reference))
    val requested = ScaffoldingResult[String, MessengerModel]("a", ScaffoldingStatus.ScaffoldingRequestedButNotFinished(), tutor, None, t(1))
    val started = empty.addScaffolding(requested)
    assertEquals(started.scaffoldingEvents.map(_.getClass.getSimpleName), Set("ScaffoldingStartedEvent"))
    assertEquals(started.addScaffolding(requested), started)
    val finished = started.addScaffolding(hint("a", 2))
    assertEquals(finished.addScaffolding(hint("a", 2)), finished)
    assertEquals(finished.scaffoldingForState("a"), Some(hint("a", 2)))
  }

  test("histories built from results project the same answers and feedback") {
    val states = Set(InteractionVariableState("a", MINOR, t(1)), InteractionVariableState("b", MAJOR, t(2)))
    val built = InteractionVariableHistory.fromResults[F](format, states, Set(GradedState("b", Map(spelling -> graded("b", 3)))), Set(hint("b", 4)))
    assertEquals(built.historyStates.events, states)
    assertEquals(built.gradingForState(Seq(spelling), "b").graderResultMap(spelling), graded("b", 3))
    assertEquals(built.scaffoldingForState("b"), Some(hint("b", 4)))
  }

  test("merging is idempotent, commutative and associative; a different event under a known id is rejected") {
    val a = history(answer("a", 1), gradingDone("a", 2))
    val b = history(answer("b", 3), hintStarted("b", 4))
    val c = history(answer("c", 5, MAJOR))
    assertEquals(a.mergeWith(a), a)
    assertEquals(a.mergeWith(b), b.mergeWith(a))
    assertEquals(a.mergeWith(b).mergeWith(c), a.mergeWith(b.mergeWith(c)))
    assertEquals(a.mergeWith(Seq(b, c)), a.mergeWith(b).mergeWith(c))
    val merged = a.mergeWith(Seq(b, c))
    assertEquals((merged.size, merged.timestampLastChanged), (5, Some(t(5))))
    val original = AnswerChangedEvent[F]("draft", t(1), MINOR)
    val revised = original.copy[F](newState = "revised")
    intercept[IllegalArgumentException](history(original).mergeWith(history(revised)))
    assertEquals(history().timestampLastChanged, None)
  }

  test("cleaning drops superseded TEMPORARY answers with their own feedback and keeps the current answer") {
    val typing = history(answer("a", 1), gradingDone("a", 2), answer("typing", 3, TEMPORARY), gradingStarted("typing", 4))
    assertEquals(typing.cleanedHistory(), typing, "the current answer stays, even when it is temporary")
    val committed = history(answer("a", 1), gradingDone("a", 2), answer("typing", 3, TEMPORARY), gradingStarted("typing", 4),
      hintStarted("a", 5), answer("b", 6, MAJOR)).cleanedHistory()
    assertEquals(committed.allStates.map(_.newState), Set("a", "b"))
    assertEquals(values(committed.events.values), refs("a", "b"), "in-flight feedback of retained answers stays")
  }

  test("SYNC_MAJOR skips TEMPORARY answers; SYNC_LAST sends only the current answer") {
    val typing = history(answer("start", 0, DEFAULT), answer("a", 1, MAJOR), gradingDone("a", 2), answer("b", 3),
      gradingDone("b", 4), answer("typing", 5, TEMPORARY), gradingStarted("typing", 6))
    assertEquals(values(typing.cleanWithStrategy(EventSyncStrategy.SYNC_MAJOR).events.values), refs("a", "b"),
      "major answers and the latest committed (MINOR) answer, with feedback")
    assertEquals(values(typing.cleanWithStrategy(EventSyncStrategy.SYNC_LAST).events.values), refs("typing"))
    assertEquals(typing.cleanWithStrategy(EventSyncStrategy.SYNC_EVERYTHING), typing)
    val untouched = history(answer("start", 0, DEFAULT), gradingDone("start", 1))
    assertEquals(untouched.cleanWithStrategy(EventSyncStrategy.SYNC_LAST), untouched)
    assertEquals(untouched.cleanWithStrategy(EventSyncStrategy.SYNC_MAJOR), untouched)
  }

  test("every sync strategy is stable: selecting again keeps the same events") {
    val sample = history(answer("start", 0, DEFAULT), answer("a", 1), answer("b", 2, MAJOR), gradingDone("b", 3),
      answer("c", 4, TEMPORARY), hintStarted("c", 5), answer("d", 6), answer("e", 6), gradingDone("a", 7))
    val strategies = Seq(EventSyncStrategy.SYNC_EVERYTHING, EventSyncStrategy.SYNC_MAJOR, EventSyncStrategy.SYNC_LAST, EventSyncStrategy.KEEP_MINOR,
      EventSyncStrategy.KEEP_LAST_FIVE, EventSyncStrategy.KEEP_LAST_AND_MAJOR, EventSyncStrategy.KEEP_LAST,
      EventSyncStrategy.KEEP_LAST_MAJOR, EventSyncStrategy.Since(t(4)))
    strategies.foreach { strategy =>
      val once = sample.cleanWithStrategy(strategy)
      assertEquals(once.cleanWithStrategy(strategy), once, strategy.toString)
      assert(once.events.values.subsetOf(sample.events.values), strategy.toString)
    }
  }
}
