package it.evadid.workbook.control

import it.evadid.workbook.model.{InteractionInfo, InteractionScope}

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.State
import it.evadid.util.logging.{BasicLogger, Logger}
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.abstractions.InteractionFormat.{BinaryPixelInteractionFormat, TextInteractionFormat}
import it.evadid.workbook.interactions.basic.TextInteraction
import it.evadid.workbook.interactions.pixel.BinaryPixelInteraction
import it.evadid.workbook.sync.*
import it.evadid.workbook.sync.UpdateImportance.*
import it.evadid.workbook.sync.destination.{SyncDestinationDefinition, SyncDestinationHandlerBasic}
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.model.history.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}
import it.evadid.workbook.model.history.events.{AnswerChangedEvent, InteractionEvent, InteractionEvents}
import it.evadid.workbook.model.history.{InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.abstractions.ExecutionContextInformation
import it.evadid.workbook.interactions.pixel.BinaryPixelImage
import it.evadid.workbook.support.GatedHistoryStore
import munit.FunSuite

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}

/** The answers of one sync context: edits, restored records, sessions and connections. */
class InteractionControllerSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val logger = Logger.withNameAndPrefixes(Some("InteractionControllerSpec"), PrintToStdLogger.printNothing)
  private val syncLogger = SyncLogger(BasicLogger())
  private val context = UsageContext("program", "workbook", "student")
  private val at = LocalDateTime.of(2026, 6, 1, 8, 0)
  private type Text = TextInteractionFormat.type
  private def text(values: (String, Int)*): InteractionVariableHistory[Text] =
    InteractionVariableHistory(InteractionEvents(values.map((value, second) =>
      AnswerChangedEvent[Text](value, at.plusSeconds(second), MINOR): InteractionEvent[Text]).toSet))

  private def controllerFor(interactions: WorkbookInteractionElement[? <: InteractionFormat]*): InteractionController = {
    val controller = InteractionController(State(InteractionInfo.empty(context)))
    controller.bind(InteractionScope(context, interactions, SyncConnection.offline))
    controller
  }

  test("a bound interaction holds its default answer under a stable storage key") {
    val essay = TextInteraction("essay")
    val answers = controllerFor(essay)
    assertEquals(answers.currentValue(essay), "")
    assertEquals(answers.currentHistory(essay).allStates.map(_.updateImportance), Set(DEFAULT))
    assertEquals(answers.info.entryOption(essay).map(_.syncContext), Some(context.toSyncContext("essay_history")))
  }

  test("user edits append states; repeating the same value and importance is ignored") {
    val essay = TextInteraction("essay")
    val answers = controllerFor(essay)
    answers.setStateFromUserInteraction(essay, "draft", MINOR, at)
    answers.setStateFromUserInteraction(essay, "draft", MINOR, at.plusSeconds(1))
    assertEquals(answers.currentHistory(essay).allStates.map(_.newState), Set("draft"), "the default is superseded by the first edit")
    answers.setStateFromUserInteraction(essay, "draft", MAJOR, at.plusSeconds(2))
    assertEquals(answers.currentHistory(essay).allStates.toList.sortBy(_.timestamp).map(event => event.newState -> event.updateImportance),
      List("draft" -> MINOR, "draft" -> MAJOR))
  }

  test("answer states record user changes with the importance of the change and observe every change") {
    val essay = TextInteraction("essay")
    val answers = controllerFor(essay)
    val seen = State(Vector.empty[(String, String)])
    answers.applyUserChange(essay, "a", change => { seen.update(_ :+ (change.lastState.value -> change.newState.value)); MAJOR })
    answers.applyUserChange(essay, "a", _ => MINOR)
    assertEquals(seen.now(), Vector("" -> "a"))
    assertEquals(answers.currentState(essay).updateImportance, MAJOR, "an unchanged answer is not recorded again")
    val bound = answers.answerState(essay, MINOR)
    val observed = State(Vector.empty[String])
    bound.observable.addObserver(value => observed.update(_ :+ value))
    bound.set("typed")
    answers.setStateFromUserInteraction(essay, "restored", MAJOR, LocalDateTime.now().plusSeconds(5))
    assertEquals(bound.now(), "restored")
    assertEquals(observed.now(), Vector("a", "typed", "restored"))
  }

  test("restoring a stored record merges it; the newest answer becomes current") {
    val essay = TextInteraction("essay")
    val answers = controllerFor(essay)
    answers.setStateFromUserInteraction(essay, "local", MINOR, at.plusSeconds(1))
    answers.restoreHistory(logger, essay, InteractionHistoryJson.fromHistory(TextInteractionFormat, text("remote" -> 2, "older" -> 0)))
    assertEquals(answers.currentValue(essay), "remote")
    assertEquals(answers.currentHistory(essay).allStates.map(_.newState), Set("older", "local", "remote"))
    val before = answers.currentHistory(essay)
    answers.restoreHistories(logger, Map(context.toSyncContext(essay.keyForSerialization) ->
      InteractionHistoryJson.fromHistory(TextInteractionFormat, before),
      context.copy(scenarioId = "other").toSyncContext(essay.keyForSerialization) ->
        InteractionHistoryJson.fromHistory(TextInteractionFormat, text("other workbook" -> 9))))
    assertEquals(answers.currentHistory(essay), before, "known events and records of other contexts change nothing")
  }

  test("an unreadable, foreign or rejected stored record is discarded without touching the current history") {
    val essay = TextInteraction("essay")
    val pixels = BinaryPixelInteraction("pixels", LanguageMapContentId("spec/pixels"), BinaryPixelImage.blank(2, 2))
    val answers = controllerFor(essay, pixels)
    answers.setStateFromUserInteraction(essay, "kept", MINOR, at)
    val before = answers.info
    answers.restoreHistory(logger, essay, InteractionHistoryJson(TextInteractionFormat, "{\"not\": \"a history\"}"))
    answers.restoreHistory(logger, essay, InteractionHistoryJson.fromHistory(BinaryPixelInteractionFormat,
      InteractionVariableHistory.empty[BinaryPixelInteractionFormat.type]))
    val resized = InteractionVariableHistory[BinaryPixelInteractionFormat.type](InteractionEvents(Set(
      AnswerChangedEvent[BinaryPixelInteractionFormat.type](BinaryPixelImage.blank(3, 3), at, MAJOR))))
    answers.restoreHistory(logger, pixels, InteractionHistoryJson.fromHistory(BinaryPixelInteractionFormat, resized))
    intercept[IllegalArgumentException](answers.setStateFromUserInteraction(pixels, BinaryPixelImage.blank(3, 3), MAJOR, at))
    assertEquals(answers.info, before)
  }

  test("a new session starts from default answers; the retired state keeps its own") {
    val essay = TextInteraction("essay")
    val answers = controllerFor(essay)
    answers.setStateFromUserInteraction(essay, "draft", MAJOR, at)
    answers.resetLocalHistory(essay)
    assertEquals(answers.currentValue(essay), "")
    answers.setStateFromUserInteraction(essay, "mine", MAJOR, at)
    val retired = answers.bind(InteractionScope(context.copy(userId = "next"), Seq(essay), SyncConnection.offline))
    assertEquals(answers.currentValue(essay), "")
    assertNotEquals(answers.info.session, retired.session)
    assertEquals(retired.historyOf(essay).historyStates.lastStateOption.map(_.value), Some("mine"))
  }

  test("interactions outside the state read their default answer and ignore writes, also under a shared key") {
    val bound = TextInteraction("essay")
    val sameKey = TextInteraction("essay")
    val answers = controllerFor(bound)
    answers.setStateFromUserInteraction(sameKey, "elsewhere", MAJOR)
    assertEquals((answers.currentValue(bound), answers.currentValue(sameKey)), ("", ""))
    assert(!answers.info.contains(sameKey))
  }

  test("textarea edits are classified by timing, length and the typed character") {
    def change(before: String, after: String, seconds: Long = 1) = TextInteraction.decideTextareaUpdateImportance(
      InteractionVariableStateChanged(InteractionVariableState(before, MINOR, at), DesignatedInteractionState(after, at.plusSeconds(seconds))))
    assertEquals(change("same", "same"), TEMPORARY)
    assertEquals(change("a", "ab", seconds = 11), MAJOR)
    assertEquals(change("a", "a."), MAJOR)
    assertEquals(change("a", "a,"), MINOR)
    assertEquals(change("a", "ab"), TEMPORARY)
    assertEquals(change("abc", "a"), MINOR)
  }

  // SYNCHRONIZATION

  private def connection(store: GatedHistoryStore): SyncConnection = store.connection()
  private def connected(store: GatedHistoryStore, interactions: WorkbookInteractionElement[? <: InteractionFormat]*) =
    store.controllerFor(interactions*)
  private def seed(store: GatedHistoryStore, interaction: TextInteraction, answer: String): Unit = store.seed(interaction,
    InteractionVariableHistory.empty[Text].addEvent(AnswerChangedEvent(answer, LocalDateTime.now().plusDays(1), MAJOR)))

  private def confirmed(answers: InteractionController, interaction: TextInteraction): Int =
    answers.report(interaction).destinationsWithSufficientWrites.size

  test("an observed change requests a store of the changed history only; a new session's binding requests none") {
    val essay = TextInteraction("essay")
    val note = TextInteraction("note")
    val requested = State(Vector.empty[String])
    val answers = new InteractionController(State(InteractionInfo.empty(context))) {
      override protected def requestStore(key: String): Unit = requested.update(_ :+ key)
    }
    answers.bind(InteractionScope(context, Seq(essay, note), SyncConnection.offline))
    answers.setStateFromUserInteraction(essay, "answer", MAJOR)
    answers.setStateFromUserInteraction(essay, "answer", MAJOR)
    answers.bind(InteractionScope(context.copy(userId = "next"), Seq(essay, note), SyncConnection.offline))
    assertEquals(requested.now(), Vector(essay.keyForSerialization))
  }

  test("a stored and read answer is confirmed; a new connection keeps the answers but not the confirmation") {
    val essay = TextInteraction("essay")
    val store = GatedHistoryStore(context)
    val answers = connected(store, essay)
    answers.setStateFromUserInteraction(essay, "answer", MAJOR)
    answers.store(essay, forceFlush = true).flatMap(_ => answers.fetch(essay, forceFlush = true)).map { _ =>
      assertEquals(confirmed(answers, essay), 1)
      val session = answers.info.session
      answers.connect(connection(GatedHistoryStore(context)))
      assertEquals(confirmed(answers, essay), 0)
      assertEquals(answers.currentValue(essay), "answer")
      assertEquals(answers.info.session, session)
    }
  }

  test("results of a replaced connection neither confirm nor change the answers") {
    val essay = TextInteraction("essay")
    val retired = GatedHistoryStore(context)
    val answers = connected(retired, essay)
    answers.setStateFromUserInteraction(essay, "answer", MAJOR)
    val held = Promise[Unit]()
    retired.gate.set(Some(held))
    seed(retired, essay, "retired remote answer")
    val write = answers.store(essay, forceFlush = true)
    val read = answers.fetch(essay, forceFlush = true)
    answers.connect(connection(GatedHistoryStore(context)))
    held.success(())
    write.flatMap(_ => read).map { _ =>
      assertEquals(confirmed(answers, essay), 0)
      assertEquals(answers.currentValue(essay), "answer")
    }
  }

  test("results of a replaced session reach only that session") {
    val essay = TextInteraction("essay")
    val store = GatedHistoryStore(context)
    val answers = connected(store, essay)
    val held = Promise[Unit]()
    store.gate.set(Some(held))
    seed(store, essay, "first user's answer")
    val read = answers.fetch(essay, forceFlush = true)
    answers.bind(InteractionScope(context.copy(userId = "next"), Seq(essay), SyncConnection.offline))
    held.success(())
    read.map(_ => assertEquals(answers.currentValue(essay), ""))
  }

  test("a read answer that the exercise rejects is discarded") {
    val pixels = BinaryPixelInteraction("pixels", LanguageMapContentId("spec/pixels"), BinaryPixelImage.blank(2, 2))
    val store = GatedHistoryStore(context)
    val answers = connected(store, pixels)
    val resized = InteractionVariableHistory[BinaryPixelInteractionFormat.type](InteractionEvents(Set(
      AnswerChangedEvent[BinaryPixelInteractionFormat.type](BinaryPixelImage.blank(3, 3), LocalDateTime.now(), MAJOR))))
    store.values.set(Map(SyncContext.serializer.serialize(context.toSyncContext(pixels.keyForSerialization)) ->
      InteractionHistoryJson.fromHistory(BinaryPixelInteractionFormat, resized).serialized))
    answers.fetch(pixels, forceFlush = true).map(_ => assertEquals(answers.currentValue(pixels), BinaryPixelImage.blank(2, 2)))
  }
}
