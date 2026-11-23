package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.InteractionFormat.{ProgrammingExerciseWithLlmScaffoldingAndGrading, TextInteractionFormat}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.LocalStorageSyncDefinition
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.{AnswerChangedEvent, InteractionEvent, InteractionEvents}
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

  private def history[F <: InteractionFormat](values: (InteractionFormat.State[F], Int)*): InteractionVariableHistory[F] =
    InteractionVariableHistory(InteractionEvents(values.map((value, second) =>
      AnswerChangedEvent[F](value, at.plusSeconds(second), UpdateImportance.MAJOR): InteractionEvent[F]).toSet))

  private def destination[F <: InteractionFormat](format: F, context: UsageContext = usage) = {
    // Built inside a test, after the DOM suite has installed `window`.
    val handler = LocalStorageSyncHandler(definition).withSyncLogger(logger).forHistory()
    FormattedSyncDestination(SyncInformationWithContext(SyncInformation(definition, EventSyncStrategy.SYNC_EVERYTHING), context, _ => handler), format)
  }

  private def cache[F <: InteractionFormat](target: FormattedSyncDestination[F]) =
    RemoteHistoryCache[SyncContext, F, FormattedSyncDestination[F]](logger, Map(target -> EventSyncStrategy.SYNC_EVERYTHING))

  test("local storage restores text and programming histories in a fresh session, then adds new events") {
    val text = TextInteractionFormat
    val programming = ProgrammingExerciseWithLlmScaffoldingAndGrading
    val answerKey = usage.toSyncContext("answer")
    val programKey = usage.toSyncContext("program")
    val program: ProgrammingState = ProgrammingStateRaw(Python, "n = 2\nfor _ in range(n):\n    forward(5)\n").toBeExpression
    val texts = destination(text)
    val programs = destination(programming)
    for {
      _ <- cache(texts).requestStore(texts, Map(answerKey -> history[text.type]("a" -> 1, "b" -> 2)))
      _ <- cache(programs).requestStore(programs, Map(programKey -> history[programming.type](program -> 1)))
      restoredTexts <- cache(destination(text)).fetchAll(Set(answerKey))
      restoredPrograms <- cache(destination(programming)).fetchAll(Set(programKey))
      _ <- cache(texts).requestStore(texts, Map(answerKey -> history[text.type]("c" -> 3)))
      again <- cache(destination(text)).fetchAll(Set(answerKey))
    } yield {
      assertEquals(restoredTexts(answerKey).map(_.associatedState), Seq("a", "b"))
      assertEquals(restoredPrograms(programKey).map(_.associatedState), Seq(program))
      assertEquals(again(answerKey).map(_.associatedState), Seq("a", "b", "c"), "stored deltas merge instead of overwriting")
    }
  }

  test("local storage keeps users apart; full and range reads discard an unreadable entry without losing the others") {
    val text = TextInteractionFormat
    val mine = destination(text)
    val theirs = destination(text, usage.copy(userId = "other"))
    val good = usage.toSyncContext("good")
    val broken = usage.toSyncContext("broken")
    for {
      _ <- cache(mine).requestStore(mine, Map(good -> history[text.type]("mine" -> 1), broken -> history[text.type]("lost" -> 1)))
      _ <- cache(theirs).requestStore(theirs, Map(usage.copy(userId = "other").toSyncContext("good") -> history[text.type]("theirs" -> 1)))
      _ = dom.window.localStorage.setItem(SyncContext.serializer.serialize(broken), "{\"not\": \"a history\"}")
      // The saved login shares the browser storage; it is not a history and is skipped.
      _ = dom.window.localStorage.setItem("edusquirrel-alluserinfo", "{\"user\": {}, \"config\": {}}")
      restored <- cache(destination(text)).fetchAll(Set(good, broken))
      window <- cache(destination(text)).fetchEventsBetween(Seq(good, broken), at, at.plusSeconds(5))
    } yield {
      assertEquals(restored(good).map(_.associatedState), Seq("mine"), "another user's entry for the same key is not mixed in")
      assertEquals(restored.getOrElse(broken, Nil), Nil)
      assertEquals((window(good).map(_.associatedState), window.getOrElse(broken, Nil)), (Seq("mine"), Nil), "range reads discard it too")
    }
  }

  test("polls confirm an answer created before an empty read and stop writing it") {
    val text = TextInteractionFormat
    val target = FormattedSyncDestination(SyncInformationWithContext(SyncInformation(definition, EventSyncStrategy.SYNC_LAST), usage,
      _ => LocalStorageSyncHandler(definition).withSyncLogger(logger).forHistory()), text)
    val polled = RemoteHistoryCache[SyncContext, text.type, FormattedSyncDestination[text.type]](logger, Map(target -> EventSyncStrategy.SYNC_LAST))
    val key = usage.toSyncContext("untouched")
    val storageKey = SyncContext.serializer.serialize(key)
    // An unanswered exercise: its DEFAULT answer is older than the first (empty) read of the destination.
    val untouched = InteractionVariableHistory[text.type](InteractionEvents(Set(AnswerChangedEvent[text.type]("", at, UpdateImportance.DEFAULT))))
    def poll() = {
      polled.mergeLocal(Map(key -> untouched))
      polled.refreshFrom(target, Set(key), LocalDateTime.now().minusMinutes(5)).flatMap(_ => polled.requestStore(target, Map(key -> untouched)))
    }
    for {
      _ <- poll()
      written = dom.window.localStorage.getItem(storageKey)
      _ <- poll()
      _ = dom.window.localStorage.setItem(storageKey, "marker")
      _ <- poll()
    } yield {
      assert(written != null, "the first poll writes the answer")
      assertEquals(dom.window.localStorage.getItem(storageKey), "marker", "once read back, the answer is not written again")
    }
  }
}
