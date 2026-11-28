package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.InteractionFormat.{ProgrammingExerciseWithLlmScaffoldingAndGrading, TextInteractionFormat}
import it.evadid.workbook.interactions.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.workbook.sync.*
import it.evadid.workbook.sync.destination.SyncDestinationDefinition.LocalStorageSyncDefinition
import it.evadid.workbook.history.InteractionVariableHistory
import it.evadid.workbook.history.events.{AnswerChangedEvent, InteractionEvent, InteractionEvents}
import org.scalajs.dom
import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

/** Histories cached in the browser's local storage come back unchanged in a fresh session. */
class BrowserHistoryStorageRoundTripSpec extends EditorDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private val logger = SyncLogger(BasicLogger())
  private val at = LocalDateTime.of(2026, 3, 1, 9, 0)
  private val usage = UsageContext("program", "workbook", "student")
  private val definition = LocalStorageSyncDefinition(50000)

  override def beforeEach(context: BeforeEach): Unit = dom.window.localStorage.clear()

  private def history[F <: InteractionFormat](format: F)(values: (InteractionFormat.State[F], Int)*): InteractionVariableHistory[F] =
    InteractionVariableHistory(format, InteractionEvents(values.map((value, second) =>
      AnswerChangedEvent[F](value, at.plusSeconds(second), UpdateImportance.MAJOR): InteractionEvent[F]).toSet))

  private def destination[F <: InteractionFormat](format: F, context: UsageContext = usage) = {
    // Built inside a test, after the DOM suite has installed `window`.
    val handler = LocalStorageSyncHandler(definition).withSyncLogger(logger).forHistory()
    FormattedSyncDestination(SyncInformationWithContext(SyncInformation(definition, EventSyncStrategy.SYNC_EVERYTHING), context, _ => handler), format)
  }

  private def cache[F <: InteractionFormat](target: FormattedSyncDestination[F]) =
    RemoteHistoryCache[SyncContext, F, FormattedSyncDestination[F]](target.format, logger, Map(target -> EventSyncStrategy.SYNC_EVERYTHING))

  test("local storage restores text and programming histories in a fresh session, then adds new events") {
    val text = TextInteractionFormat
    val programming = ProgrammingExerciseWithLlmScaffoldingAndGrading
    val answerKey = usage.toSyncContext("answer")
    val programKey = usage.toSyncContext("program")
    val program: ProgrammingState = ProgrammingStateRaw(Python, "n = 2\nfor _ in range(n):\n    forward(5)\n").toBeExpression
    val texts = destination(text)
    val programs = destination(programming)
    for {
      _ <- cache(texts).requestStore(texts, Map(answerKey -> history[text.type](text)("a" -> 1, "b" -> 2)))
      _ <- cache(programs).requestStore(programs, Map(programKey -> history[programming.type](programming)(program -> 1)))
      restoredTexts <- cache(destination(text)).fetchAll(Set(answerKey))
      restoredPrograms <- cache(destination(programming)).fetchAll(Set(programKey))
      _ <- cache(texts).requestStore(texts, Map(answerKey -> history[text.type](text)("c" -> 3)))
      again <- cache(destination(text)).fetchAll(Set(answerKey))
    } yield {
      assertEquals(restoredTexts(answerKey).collect { case answer: AnswerChangedEvent[?] => answer.newState }, Seq("a", "b"))
      assertEquals(restoredPrograms(programKey).collect { case answer: AnswerChangedEvent[?] => answer.newState }, Seq(program))
      assertEquals(again(answerKey).collect { case answer: AnswerChangedEvent[?] => answer.newState }, Seq("a", "b", "c"), "stored deltas merge instead of overwriting")
    }
  }

  test("local storage keeps users apart; full and range reads discard an unreadable entry without losing the others") {
    val text = TextInteractionFormat
    val mine = destination(text)
    val theirs = destination(text, usage.copy(userId = "other"))
    val good = usage.toSyncContext("good")
    val broken = usage.toSyncContext("broken")
    for {
      _ <- cache(mine).requestStore(mine, Map(good -> history[text.type](text)("mine" -> 1), broken -> history[text.type](text)("lost" -> 1)))
      _ <- cache(theirs).requestStore(theirs, Map(usage.copy(userId = "other").toSyncContext("good") -> history[text.type](text)("theirs" -> 1)))
      _ = dom.window.localStorage.setItem(SyncContext.serializer.serialize(broken), "{\"not\": \"a history\"}")
      // The saved login shares the browser storage; it is not a history and is skipped.
      _ = dom.window.localStorage.setItem("edusquirrel-alluserinfo", "{\"user\": {}, \"config\": {}}")
      restored <- cache(destination(text)).fetchAll(Set(good, broken))
      window <- cache(destination(text)).fetchEventsBetween(Seq(good, broken), at, at.plusSeconds(5))
    } yield {
      assertEquals(restored(good).collect { case answer: AnswerChangedEvent[?] => answer.newState }, Seq("mine"), "another user's entry for the same key is not mixed in")
      assertEquals(restored.getOrElse(broken, Nil), Nil)
      assertEquals((window(good).collect { case answer: AnswerChangedEvent[?] => answer.newState }, window.getOrElse(broken, Nil)), (Seq("mine"), Nil), "range reads discard it too")
    }
  }
}
