package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.storage.remote.RemoteHistoryCache
import it.evadid.sql.{BackendDatabase, DatabaseHistoryFormat}
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.InteractionFormat.{ProgrammingExerciseWithLlmScaffoldingAndGrading, TextInteractionFormat}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.{SyncDestinationBasedOnHistoryDatabase, defaultBlobDatabase, defaultDatabase}
import it.evadid.workbook.interaction.sync.destination.SyncDestinationHistoryHandler
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.{AnswerChangedEvent, InteractionEvent, InteractionEvents}
import java.time.LocalDateTime
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future}

/** Histories stored through the client's caches and database handler come back unchanged from a dummy
  * history database with the server's semantics, in both storage layouts and in a fresh session.
  */
class DatabaseHistoryRoundTripSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val logger = SyncLogger(BasicLogger())
  private val at = LocalDateTime.of(2026, 3, 1, 9, 0)
  private val usage = UsageContext("program", "workbook", "student")
  private val text = TextInteractionFormat
  private val programming = ProgrammingExerciseWithLlmScaffoldingAndGrading
  private val layouts = Seq(defaultDatabase, defaultBlobDatabase)

  private def handler(database: InMemoryHistoryDatabase, definition: SyncDestinationBasedOnHistoryDatabase): SyncDestinationHistoryHandler =
    DatabaseSyncViaBackendServerHandler(logger, definition, database.client, BackendDatabase)

  private def textHistory(values: (String, Int)*): InteractionVariableHistory[text.type] =
    InteractionVariableHistory(InteractionEvents(values.map((value, second) =>
      AnswerChangedEvent[text.type](value, at.plusSeconds(second), UpdateImportance.MAJOR): InteractionEvent[text.type]).toSet))

  /** A programming answer whose expression tree uses computed repeat counts and one-way counting loops. */
  private val program: ProgrammingState =
    ProgrammingStateRaw(Python, "n = 3\nfor _ in range(n):\n    forward(10)\nfor i in range(n, 0, -1):\n    right(i)\n").toBeExpression

  private val programmingHistory: InteractionVariableHistory[programming.type] =
    InteractionVariableHistory(InteractionEvents(Set(
      AnswerChangedEvent[programming.type](program, at, UpdateImportance.MAJOR): InteractionEvent[programming.type],
      AnswerChangedEvent[programming.type](ProgrammingStateRaw(Python, "forward(1)"), at.plusSeconds(5), UpdateImportance.MINOR))))

  private def destination[F <: InteractionFormat](format: F, history: SyncDestinationHistoryHandler,
      definition: SyncDestinationBasedOnHistoryDatabase, context: UsageContext = usage) =
    FormattedSyncDestination(SyncInformationWithContext(SyncInformation(definition, EventSyncStrategy.SYNC_EVERYTHING), context, _ => history), format)

  private def cache[F <: InteractionFormat](target: FormattedSyncDestination[F]) =
    RemoteHistoryCache[SyncContext, F, FormattedSyncDestination[F]](logger, Map(target -> EventSyncStrategy.SYNC_EVERYTHING))

  layouts.foreach { definition =>
    val layout = definition.format.toString

    test(s"$layout: the database handler stores histories of several formats and reads them back unchanged") {
      val database = InMemoryHistoryDatabase()
      val history = handler(database, definition)
      val answerKey = usage.toSyncContext("answer")
      val programKey = usage.toSyncContext("program")
      val answers = textHistory("first" -> 1, "second" -> 2)
      history.storeHistoriesJson(Map(
        answerKey -> InteractionHistoryJson.fromHistory(text, answers).serialized,
        programKey -> InteractionHistoryJson.fromHistory(programming, programmingHistory).serialized)).flatMap { _ =>
        assertEquals(database.commands.now(), Vector("bulkStore"), "one request per batch")
        history.readHistoriesJson(usage).map { fetched =>
          val restored = fetched.fetchedValues.map(value => value.dataKey -> InteractionHistoryJson.deserialize(value.dataValue)).toMap
          assertEquals(restored(answerKey).typedAs(text), answers)
          assertEquals(restored(programKey).typedAs(programming), programmingHistory)
          assertEquals(restored(programKey).typedAs(programming).latestAnswerEvents.map(_.associatedState), Set(ProgrammingStateRaw(Python, "forward(1)"): ProgrammingState))
        }
      }
    }

    test(s"$layout: a fresh session's cache restores what another session's cache stored, then reads only new events") {
      val database = InMemoryHistoryDatabase()
      val key = usage.toSyncContext("answer")
      val reader = cache(destination(text, handler(database, definition), definition))
      val writerDestination = destination(text, handler(database, definition).toBuffered, definition)
      val writing = cache(writerDestination)
      for {
        _ <- writing.requestStore(writerDestination, Map(key -> textHistory("a" -> 1, "b" -> 2)), forceFlush = true)
        restored <- reader.fetchAll(Set(key))
        _ = assertEquals(restored(key).map(_.associatedState), Seq("a", "b"))
        _ <- writing.requestStore(writerDestination, Map(key -> textHistory("c" -> 3)), forceFlush = true)
        _ <- reader.ensureMaxAge(Set(key))
      } yield {
        assertEquals(reader.updateReport.now().get(key).events.values.map(_.associatedState), Set("a", "b", "c"))
        assert(database.commands.now().count(_ == "bulkStore") == 2, database.commands.now().toString)
      }
    }

    test(s"$layout: range reads return exactly the events of the inclusive window") {
      val database = InMemoryHistoryDatabase()
      val key = usage.toSyncContext("answer")
      val target = destination(text, handler(database, definition), definition)
      val session = cache(target)
      session.requestStore(target, Map(key -> textHistory("a" -> 1, "b" -> 2, "c" -> 3, "d" -> 4))).flatMap { _ =>
        session.fetchEventsBetween(Seq(key), at.plusSeconds(2), at.plusSeconds(3)).map { window =>
          assertEquals(window(key).map(_.associatedState), Seq("b", "c"))
        }
      }
    }

    test(s"$layout: users and workbooks stay apart; clearing a key and resetting a user delete only what they name") {
      val database = InMemoryHistoryDatabase()
      val other = usage.copy(userId = "other")
      val nextWorkbook = usage.copy(scenarioId = "next")
      val history = handler(database, definition)
      def json(value: String) = InteractionHistoryJson.fromHistory(text, textHistory(value -> 1)).serialized
      def keys(context: UsageContext) = history.readHistoriesJson(context).map(_.fetchedValues.map(_.dataKey.keyForSerialisation).toSet)
      for {
        _ <- history.storeHistoriesJson(Map(usage.toSyncContext("a") -> json("a"), usage.toSyncContext("b") -> json("b"),
          nextWorkbook.toSyncContext("a") -> json("next"), other.toSyncContext("a") -> json("other")))
        _ <- history.clearValue(usage.toSyncContext("a"))
        afterClear <- keys(usage)
        _ = assertEquals(afterClear, Set("b"))
        _ <- history.clearAllHistories(usage)
        mine <- keys(usage)
        mineNext <- keys(nextWorkbook)
        theirs <- keys(other)
      } yield {
        assertEquals((mine, mineNext), (Set.empty[String], Set.empty[String]), "a reset covers every workbook of the user")
        assertEquals(theirs, Set("a"), "other users keep their histories")
      }
    }

    test(s"$layout: a malformed saved record is discarded while the others are restored") {
      val database = InMemoryHistoryDatabase()
      val good = usage.toSyncContext("good")
      val broken = usage.toSyncContext("broken")
      val target = destination(text, handler(database, definition), definition)
      val session = cache(target)
      session.requestStore(target, Map(good -> textHistory("kept" -> 1), broken -> textHistory("lost" -> 1))).flatMap { _ =>
        database.corrupt(definition.format, broken, database.rows.now()(definition.format -> good))
        cache(target).fetchAll(Set(good, broken)).map { restored =>
          assertEquals(restored.get(good).map(_.map(_.associatedState)), Some(Seq("kept")))
          assertEquals(restored.getOrElse(broken, Nil), Nil)
        }
      }
    }

    test(s"$layout: a write that fails while offline stays pending and is completed later") {
      val database = InMemoryHistoryDatabase()
      val key = usage.toSyncContext("answer")
      val target = destination(text, handler(database, definition), definition)
      val session = cache(target)
      database.offline.set(true)
      session.requestStore(target, Map(key -> textHistory("offline" -> 1))).failed.flatMap { _ =>
        assertEquals(session.reports.now().get(key).pendingWrites(target).events.values.size, 1)
        database.offline.set(false)
        session.storeAll().flatMap(_ => cache(target).fetchAll(Set(key))).map { restored =>
          assertEquals(restored(key).map(_.associatedState), Seq("offline"))
          assertEquals(session.reports.now().get(key).syncedDestinations, Set(target))
        }
      }
    }
  }

  test("a context keeps its format: storing another format for it fails and leaves the saved history") {
    val database = InMemoryHistoryDatabase()
    val history = handler(database, defaultDatabase)
    val key = usage.toSyncContext("answer")
    history.storeHistoryJson(key, InteractionHistoryJson.fromHistory(text, textHistory("a" -> 1)).serialized).flatMap { _ =>
      history.storeHistoryJson(key, InteractionHistoryJson.fromHistory(programming, programmingHistory).serialized).transform(scala.util.Success(_)).map { outcome =>
        assert(outcome.isFailure, outcome.toString)
        assertEquals(database.rows.now()(DatabaseHistoryFormat.RelationalFormat -> key).typedAs(text).events.values.map(_.associatedState), Set("a"))
      }
    }
  }
}
