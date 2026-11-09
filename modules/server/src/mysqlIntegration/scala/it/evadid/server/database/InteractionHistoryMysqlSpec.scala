package it.evadid.server.database

import it.evadid.core.datastructures.chat.{Message, MessengerModel}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.sql.DatabaseHistoryFormat.*
import it.evadid.server.commandHandler.sql.sync.{DeleteInDatabase, FetchFromDatabase, UpsertToDatabase}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.elements.interactionElements.gpt.{TextInputLlmGrader, TextInputWithScaffoldingAndGrading}
import it.evadid.workbook.interaction.sync.{UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.variable.*
import it.evadid.util.logging.BasicLogger
import java.time.LocalDateTime
import scala.util.Using
import munit.FunSuite
import upickle.default.*

class InteractionHistoryMysqlSpec extends FunSuite {
  private type History = InteractionVariableHistory[String, MessengerModel, MessengerModel]
  private val logger = BasicLogger()
  private val context = UsageContext("program", "scenario", "student")
  private val key = "answer_history"
  private val sync = context.toSyncContext(key)
  private val database = "edusquirrel_account_test"
  private val time = LocalDateTime.of(2026, 11, 9, 12, 13, 14, 123456789)
  private val transcript = MessengerModel(List(Message("entity-specific transcript", MessengerModel.pAgent)))
  private def grader(index: Int) = TextInputLlmGrader(s"$key:$index", LanguageMapContentId("test/task"), None,
    TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(LanguageMapContentId("test/criterion"), Map.empty))
  private def history(value: String = "first", at: LocalDateTime = time): History = {
    val grades: Map[WorkbookInteractionGrader[String, MessengerModel], GradingResult[String, MessengerModel]] = (0 to 1).map { index =>
      val check = grader(index)
      val result = GradingResult(check, value, GradingStatus.Correct(), Some(LanguageMap.universalMap("regular grading feedback")),
        Some(transcript), at.plusSeconds(1))
      check -> result
    }.toMap
    val scaffolding = ScaffoldingResult(value, ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap("regular scaffolding feedback")),
      FeedbackEntity.LlmEntity(), Some(transcript), at.plusSeconds(2))
    InteractionVariableHistory[String, MessengerModel, MessengerModel](Set(InteractionVariableState(value, UpdateImportance.MAJOR, at)),
      Set(GradedState(value, grades)), Set(scaffolding))
  }
  private def store(history: History, format: DatabaseHistoryFormat = RelationalEvents): Unit =
    UpsertToDatabase.handleRequest(StoreToDbRequest(sync, write(history), database, format), logger)
  private def fetch(format: DatabaseHistoryFormat = RelationalEvents): Map[it.evadid.workbook.interaction.sync.SyncContext, String] =
    FetchFromDatabase.handleRequest(FetchAllFromDbRequest(context, database, None, format), logger).fetchedElements
  private def scalar(sql: String): Long = MysqlTestDatabase.withConnection { connection =>
    Using.resource(connection.createStatement())(statement => Using.resource(statement.executeQuery(sql))(rows => { rows.next(); rows.getLong(1) }))
  }
  override def beforeEach(context: BeforeEach): Unit = MysqlTestDatabase.withConnection(MysqlTestDatabase.reset)

  test("all three supported formats round-trip current answers, multiple graders and scaffolding") {
    List(ClassicKeylessJson, ClassicJsonWithKey, RelationalEvents).foreach { format =>
      val saved = history()
      store(saved, format)
      assertEquals(read[History](fetch(format)(sync)), saved)
      DeleteInDatabase.handleRequest(DeleteInDbRequest(context, Some(key), database, format), logger)
      assertEquals(fetch(format), Map.empty)
    }
  }

  test("nanosecond-distinct answers survive, retries retain IDs, and context IDs never enter payloads") {
    store(history())
    val answerId = scalar("SELECT id FROM answer_event")
    val syncId = scalar("SELECT id FROM sync_context_id")
    store(history())
    assertEquals(scalar("SELECT id FROM answer_event"), answerId)
    assertEquals(scalar("SELECT id FROM sync_context_id"), syncId)
    assertEquals(scalar("SELECT COUNT(*) FROM grading_event"), 2L)
    store(history("second", time.plusNanos(1)))
    val restored = read[History](fetch()(sync))
    assertEquals(restored.historyStates.events.map(_.timestamp), Set(time, time.plusNanos(1)))
    assertEquals(restored.historyStates.lastStateOption.map(_.value), Some("second"))
    assertEquals(restored.historyGrading.gradingsMap.keySet, Set("first", "second"))
    assert(!fetch()(sync).contains("answer_id"))
    assert(!fetch()(sync).contains("sync_id"))
  }

  test("replacing an answer at the same timestamp retains its ID and invalidates old feedback") {
    store(history())
    val id = scalar("SELECT id FROM answer_event")
    val replacement = InteractionVariableHistory[String, MessengerModel, MessengerModel](
      Set(InteractionVariableState("replacement", UpdateImportance.MINOR, time)))
    store(replacement)
    assertEquals(scalar("SELECT id FROM answer_event"), id)
    assertEquals(scalar("SELECT COUNT(*) FROM grading_event"), 0L)
    assertEquals(scalar("SELECT COUNT(*) FROM scaffolding_event"), 0L)
    assertEquals(read[History](fetch()(sync)), replacement)
  }

  test("regular feedback and entity-specific data are stored in separate JSON columns") {
    store(history())
    MysqlTestDatabase.withConnection { connection =>
      List("grading_event", "scaffolding_event").foreach { table =>
        Using.resource(connection.createStatement()) { statement =>
          Using.resource(statement.executeQuery(s"SELECT serialized_data,additional_data FROM $table")) { rows =>
            while rows.next() do {
              assert(!rows.getString(1).contains("entity-specific transcript"))
              assert(rows.getString(2).contains("entity-specific transcript"))
              assert(!rows.getString(2).contains("regular grading feedback"))
            }
          }
        }
      }
    }
  }

  test("feedback without a matching answer rolls back every inserted row") {
    val saved = history()
    val invalid = saved.copy(historyStates = InteractionVariableStateHistory(Set.empty))
    intercept[IllegalArgumentException](store(invalid))
    assertEquals(scalar("SELECT COUNT(*) FROM sync_context_id"), 0L)
    assertEquals(scalar("SELECT COUNT(*) FROM answer_event"), 0L)
  }

  test("reads, deletes and feedback foreign keys stay within their sync context") {
    store(history())
    val other = context.copy(userId = "other-student").toSyncContext(key)
    UpsertToDatabase.handleRequest(StoreToDbRequest(other, write(history()), database, RelationalEvents), logger)
    DeleteInDatabase.handleRequest(DeleteInDbRequest(context, None, database, RelationalEvents), logger)
    assertEquals(fetch(), Map.empty)
    val retained = FetchFromDatabase.handleRequest(FetchAllFromDbRequest(other.toUsageContext, database, None, RelationalEvents), logger)
    assertEquals(read[History](retained.fetchedElements(other)), history())
    MysqlTestDatabase.withConnection { connection =>
      Using.resource(connection.createStatement()) { statement =>
        intercept[java.sql.SQLException](statement.executeUpdate("UPDATE grading_event SET sync_id=999999"))
      }
    }
  }

  test("schema inspection and setup leave existing rows untouched and create nothing on conflict") {
    store(history())
    MysqlTestDatabase.withConnection { connection =>
      assert(EnsureDatabaseProperlySetup.inspect(connection).valid)
      assert(!EnsureDatabaseProperlySetup.setupIfEmpty(connection).created)
      assertEquals(read[History](fetch()(sync)), history())
      MysqlTestDatabase.clearSchema(connection)
      Using.resource(connection.createStatement()) { statement =>
        statement.executeUpdate("CREATE TABLE user (id INT NOT NULL PRIMARY KEY, marker VARCHAR(20))")
        statement.executeUpdate("INSERT INTO user VALUES (1,'untouched')")
        val before = EnsureDatabaseProperlySetup.inspect(connection)
        assert(before.conflicts.nonEmpty)
        val after = EnsureDatabaseProperlySetup.setupIfEmpty(connection)
        assertEquals(after.existingTables, Set("user"))
        assert(!after.created)
        Using.resource(statement.executeQuery("SELECT marker FROM user WHERE id=1")) { rows =>
          assert(rows.next()); assertEquals(rows.getString(1), "untouched")
        }
      }
    }
  }

  test("late feedback does not create edits or replace a newer answer, and pending retries do not erase completed feedback") {
    val first = history()
    store(first)
    store(history("second", time.plusNanos(1)))
    val late = first.copy(historyStates = InteractionVariableStateHistory(Set.empty))
    store(late)
    val pending = first.copy(historyGrading = InteractionVariableGradingHistory.fromResults(
      first.historyGrading.gradingsFinished.flatMap(_.testsWithResults).map(result =>
        result.copy(gradingStatus = GradingStatus.NotFinished(), gradingTime = time.plusDays(1), additionalData = None))))
    store(pending)
    val restored = read[History](fetch()(sync))
    assertEquals(restored.historyStates.events.size, 2)
    assertEquals(restored.historyStates.lastStateOption.map(_.value), Some("second"))
    assertEquals(restored.historyGrading.gradingsMap("first"), first.historyGrading.gradingsMap("first"))
  }

  test("malformed feedback is isolated on reads and can be replaced without losing other records") {
    store(history())
    MysqlTestDatabase.withConnection { connection =>
      Using.resource(connection.createStatement())(_.executeUpdate("UPDATE grading_event SET serialized_data='{}' WHERE grader_key='answer_history:0'"))
    }
    val restored = read[History](fetch()(sync))
    assertEquals(restored.historyGrading.gradingsMap("first").graderResultMap.size, 1)
    assertEquals(restored.historyScaffolding.scaffoldingsFinished.size, 1)
    store(history())
    assertEquals(read[History](fetch()(sync)), history())
  }

  test("schema inspection detects collation changes and never repairs them") {
    MysqlTestDatabase.withConnection { connection =>
      Using.resource(connection.createStatement())(_.executeUpdate(
        "ALTER TABLE sync_context_id MODIFY program_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL"))
      val report = EnsureDatabaseProperlySetup.inspect(connection)
      assert(report.conflicts.exists(_.contains("collation")), report.toString)
      assert(!EnsureDatabaseProperlySetup.setupIfEmpty(connection).created)
      assertEquals(EnsureDatabaseProperlySetup.inspect(connection).conflicts, report.conflicts)
    }
  }

  test("the interaction-history script alone creates its complete format in an empty database") {
    MysqlTestDatabase.withConnection { connection =>
      MysqlTestDatabase.clearSchema(connection)
      Using.resource(connection.createStatement()) { statement =>
        DatabaseSqlScripts.statementsFor("createInteractionHistoryTables.sql").foreach(sql => statement.executeUpdate(sql.toString))
      }
      val report = EnsureDatabaseProperlySetup.inspect(connection)
      assertEquals(report.existingTables, RelationalEvents.tables.toSet)
      assertEquals(report.conflicts, Nil)
      assert(!EnsureDatabaseProperlySetup.setupIfEmpty(connection).created)
    }
  }

  test("schema inspection rejects additional uniqueness that would change answer upsert semantics") {
    store(history())
    MysqlTestDatabase.withConnection { connection =>
      Using.resource(connection.createStatement())(_.executeUpdate("ALTER TABLE answer_event ADD UNIQUE KEY wrong_single_answer (sync_id)"))
      val report = EnsureDatabaseProperlySetup.inspect(connection)
      assert(report.conflicts.exists(_.contains("unexpected uniqueness")), report.toString)
      assert(!EnsureDatabaseProperlySetup.setupIfEmpty(connection).created)
      assertEquals(read[History](fetch()(sync)), history())
    }
  }

  test("simultaneous initializers create an empty database once and both observe a valid schema") {
    import scala.concurrent.{Await, ExecutionContext, Future}
    import scala.concurrent.duration.*
    given ExecutionContext = ExecutionContext.global
    MysqlTestDatabase.withConnection(MysqlTestDatabase.clearSchema)
    val ready = new java.util.concurrent.CountDownLatch(2)
    val attempts = List.fill(2)(Future {
      MysqlTestDatabase.withConnection { connection =>
        ready.countDown()
        assert(ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
        EnsureDatabaseProperlySetup.setupIfEmpty(connection)
      }
    })
    val results = Await.result(Future.sequence(attempts), 20.seconds)
    assert(results.forall(_.valid), results.toString)
    assertEquals(results.count(_.created), 1)
  }

  test("repeated answer values retain one grading result per logical grader key after configuration changes") {
    store(history())
    val newer = history("first", time.plusDays(1))
    val changedResults = newer.historyGrading.gradingsFinished.flatMap(_.testsWithResults).map { result =>
      if result.grader.key == s"$key:0" then result.copy(grader = grader(0).copy(exerciseText = LanguageMapContentId("test/changed-task")))
      else result
    }
    val changed = newer.copy(historyGrading = InteractionVariableGradingHistory.fromResults(changedResults))
    store(changed)
    val restored = read[History](fetch()(sync))
    assertEquals(restored.historyGrading.gradingsMap("first"), changed.historyGrading.gradingsMap("first"))
    assertEquals(restored.historyStates.events.size, 2)
  }
}
