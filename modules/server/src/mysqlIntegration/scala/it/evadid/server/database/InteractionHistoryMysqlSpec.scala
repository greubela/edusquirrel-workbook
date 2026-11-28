package it.evadid.server.database

import it.evadid.sql.{BackendDatabase, DatabaseConfigWithCredentials}
import it.evadid.workbook.abstractions.InteractionFormat.{TextInputWithLlmGradingAndScaffoldingFormat,
  TextInteractionFormat, LabeledCheckboxInteractionFormat, SqlCommandExerciseFormat}
import it.evadid.workbook.abstractions.FeedbackEntity.LlmEntity

import it.evadid.workbook.interactions.programming.state.ProgrammingState
import it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw

import it.evadid.core.datastructures.chat.{Message, MessengerModel}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId, WorkbookContent}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.sql.DatabaseHistoryFormat.*
import it.evadid.server.{AuthHandling, BackendServer}
import it.evadid.core.datastructures.user.{User, UserTokenInfo}
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.formats.ExecutionClientResponse
import play.api.Mode
import play.core.server.ServerConfig
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.GZIPOutputStream
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import it.evadid.server.commandHandler.sql.sync.HistorySql
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.history.events.*
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.interactions.gpt.{TextInputLlmGrader, TextInputWithScaffoldingAndGrading}
import it.evadid.workbook.sync.{EventSyncStrategy, SyncContext, SyncSuccess, UpdateImportance, UsageContext}
import it.evadid.workbook.history.*
import it.evadid.util.logging.{BasicLogger, Logger}
import java.time.Instant
import scala.util.Using
import it.evadid.workbook.abstractions.InteractionFormat
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

class InteractionHistoryMysqlSpec extends FunSuite {
  private val format = TextInputWithLlmGradingAndScaffoldingFormat
  // The extreme event times the sortable timestamp text supports; Instant.MIN and MAX are open range bounds.
  private val earliestEvent = LocalDateTime.MIN.toInstant(java.time.ZoneOffset.UTC)
  private val latestEvent = LocalDateTime.MAX.toInstant(java.time.ZoneOffset.UTC)
  private type History = InteractionVariableHistory[TextInputWithLlmGradingAndScaffoldingFormat.type]
  private val logger = BasicLogger()
  private val context = UsageContext("program", "scenario", "student")
  private val key = "answer_history"
  private val sync = context.toSyncContext(key)
  private val time = java.time.LocalDateTime.of(2026, 11, 9, 12, 13, 14, 123456789).toInstant(java.time.ZoneOffset.UTC)
  private val transcript = MessengerModel(List(Message("entity-specific transcript", MessengerModel.pAgent)))
  private def config = DatabaseConfigWithCredentials.readFromEnv().withoutCredentials
  private def grader(index: Int) = TextInputLlmGrader(LanguageMapContentId("test/task"), None,
    TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(LanguageMapContentId(s"test/criterion-$index"), Map.empty))
  private def history(value: String = "first", at: Instant = time): History = {
    val grades = (0 to 1).map { index =>
      GradingResult(grader(index), value, GradingStatus.Correct, Some(WorkbookContent(LanguageMap.universalMap("regular grading feedback"))),
        Some(transcript), at.plusSeconds(1))
    }
    val scaffolding = ScaffoldingResult(value, ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap("regular scaffolding feedback")),
      FeedbackEntity.LlmEntity, Some(transcript), at.plusSeconds(2))
    val result = InteractionVariableHistory.empty(format).addState(InteractionVariableState(value, UpdateImportance.MAJOR, at))
      .addGradings(grades).addScaffolding(scaffolding)
    // A fixture describes the same logical events each time it is requested.
    result.copy(events = InteractionEvents(result.events.values.map {
      case event: AnswerChangedEvent[format.type] => event.copy[format.type](eventId = fixtureId(s"answer/$value/$at"))
      case event: GradingCompletedEvent[format.type] => event.copy[format.type](eventId = fixtureId(s"grading/$value/$at/${event.grader.key}"))
      case event: ScaffoldingCompletedEvent[format.type] => event.copy[format.type](eventId = fixtureId(s"scaffolding/$value/$at"))
      case event => event
    }))
  }
  private def storeVia(request: StoreToDbRequest, logger: Logger) = HistorySql.forRequest(request, logger)(_.store(request))
  private def storeAllVia(request: BulkStoreToDbRequest, logger: Logger) = HistorySql.forRequest(request, logger)(_.storeAll(request))
  private def fetchVia(request: FetchEventsFromDbRequest, logger: Logger) = HistorySql.forRequest(request, logger)(_.fetch(request))
  private def deleteVia(request: DeleteInDbRequest, logger: Logger) = HistorySql.forRequest(request, logger)(_.delete(request))
  private def fixtureId(value: String): EventId = EventId(java.util.UUID.nameUUIDFromBytes(value.getBytes(UTF_8)).toString)
  private def encoded(history: History): InteractionHistoryJson = InteractionHistoryJson.fromHistory(format, history)
  private def store(history: History, storage: DatabaseHistoryFormat = RelationalFormat): Unit =
    storeVia(StoreToDbRequest(sync, encoded(history), config, storage), logger)
  private def fetchRequest(storage: DatabaseHistoryFormat, keys: Seq[String] = Seq(key),
                           starting: Instant = Instant.MIN, until: Instant = Instant.MAX,
                           strategy: EventSyncStrategy = EventSyncStrategy.SYNC_EVERYTHING,
                           usage: UsageContext = context): FetchEventsFromDbRequest =
    FetchEventsFromDbRequest(usage, keys, starting, until, config, storage, strategy)
  /** The typed history of [[sync]] in a direct database read. */
  private def fetchedHistory(request: FetchEventsFromDbRequest) =
    fetchVia(request, logger).fetchedElements(sync).typedAs(format)
  private def fetch(storage: DatabaseHistoryFormat = RelationalFormat): Map[SyncContext, ujson.Value] =
    fetchVia(fetchRequest(storage, keys = Seq.empty), logger).fetchedElements.map((key, value) => key -> value.historyJson)
  private def scalar(sql: String): Long = MysqlTestDatabase.withConnection { connection =>
    Using.resource(connection.createStatement())(statement => Using.resource(statement.executeQuery(sql))(rows => { rows.next(); rows.getLong(1) }))
  }
  private def command[I: Writer](name: String, request: I): ExecutionCommand = ExecutionCommand(name, writeJs(request))
  private def storeCommand(request: StoreToDbRequest): ExecutionCommand = command(StoreToDbCommand.name, request)

  private def withHttpCommands(forwardedAddress: Option[String] = None, trustedProxy: String = "192.0.2.1")
                              (run: (ExecutionCommand => ExecutionClientResponse) => Unit): Unit = {
    val properties = new java.util.Properties()
    properties.setProperty("play.http.forwarded.version", "x-forwarded")
    properties.setProperty("play.http.forwarded.trustedProxies.0", trustedProxy)
    val server = BackendServer.createServer(ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test,
      properties = properties))(BackendServer.buildApiRouter)
    val client = HttpClient.newHttpClient()
    val user = User("Sync test", context.userId, "sync-test@example.test")
    MysqlTestDatabase.withConnection { connection =>
      UserAccountRows(connection, logger)
        .upsertUser(user, it.evadid.core.datastructures.user.UserConfig.defaults(isLocalAccount = false))
    }
    val token = AuthHandling.createToken(user)
    try run { command =>
      val compressed = new ByteArrayOutputStream()
      Using.resource(new GZIPOutputStream(compressed))(_.write(write(command).getBytes(UTF_8)))
      val builder = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:${server.httpPort.get}/executeCommand"))
        .header("Content-Type", "application/json").header("Content-Encoding", "gzip")
        .header(UserTokenInfo.SignedToken.cookieKey, write(token))
        .POST(HttpRequest.BodyPublishers.ofByteArray(compressed.toByteArray))
      forwardedAddress.foreach(address => builder.header("X-Forwarded-For", address))
      val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
      assertEquals(response.statusCode(), 200)
      read[ExecutionClientResponse](response.body())
    } finally {
      client.close()
      server.stop()
    }
  }

  override def beforeEach(context: BeforeEach): Unit = MysqlTestDatabase.withConnection(MysqlTestDatabase.reset)


  Seq(BlobFormat, RelationalFormat).foreach { storage =>
    test(s"$storage bulk request stores multiple keys atomically, merges deltas and deduplicates retries") {
      val other = context.toSyncContext("other-key")
      val initial = BulkStoreToDbRequest(Vector(sync, other), Vector(encoded(history()), encoded(history("other"))), config, storage)
      val sent = history().events.ids ++ history("other").events.ids
      val first = storeAllVia(initial, logger)
      assertEquals((first.elementsAdded, first.elementsUnchanged), (sent, Set.empty[EventId]))
      assertEquals(read[History](fetch(storage)(sync)), history())
      assertEquals(read[History](fetch(storage)(other)), history("other"))
      val retry = storeAllVia(initial, logger)
      assertEquals((retry.elementsAdded, retry.elementsUnchanged), (Set.empty[EventId], sent), "a retry confirms the stored events")
      val delta = history("next", time.plusNanos(1))
      storeAllVia(initial.copy(syncContexts = Seq(sync), histories = Seq(encoded(delta))), logger)
      assertEquals(read[History](fetch(storage)(sync)), history().mergeWith(delta))
      val invalid = InteractionHistoryJson(format, """{"not": "a history"}""")
      val failed = initial.copy(histories = Seq(encoded(history("should roll back")), invalid))
      intercept[Exception](storeAllVia(failed, logger))
      assertEquals(read[History](fetch(storage)(sync)), history().mergeWith(delta))
    }
  }

  Seq(BlobFormat, RelationalFormat).foreach { storage =>
    test(s"$storage bulk histories retain independent InteractionFormats without workbook definitions") {
      val text = InteractionVariableHistory.empty(TextInteractionFormat).addEvent(
        AnswerChangedEvent[TextInteractionFormat.type]("plain answer", time, UpdateImportance.MAJOR))
      val checkbox = InteractionVariableHistory.empty(LabeledCheckboxInteractionFormat).addEvent(
        AnswerChangedEvent[LabeledCheckboxInteractionFormat.type](true, time, UpdateImportance.MAJOR))
      val contexts = Seq(sync, context.toSyncContext("unknown-text-id"), context.toSyncContext("unknown-checkbox-id"))
      val histories = Seq(encoded(history()), InteractionHistoryJson.fromHistory(TextInteractionFormat, text),
        InteractionHistoryJson.fromHistory(LabeledCheckboxInteractionFormat, checkbox))
      withHttpCommands() { send =>
        val request = BulkStoreToDbRequest(contexts, histories, BackendDatabase, storage)
        assert(send(command(bulkStoreToDbCommand.name, request)).result.isRight)
        val range = FetchEventsFromDbRequest(context, Seq.empty, Instant.MIN,
          Instant.MAX, BackendDatabase, storage, EventSyncStrategy.SYNC_EVERYTHING)
        val response = send(command(fetchEventsFromDbCommand.name, range))
        assert(response.result.isRight)
        val fetched = response.result.toOption.get.getAs[DbFetchResponse].fetchedElements
        assertEquals(fetched.keySet, contexts.toSet)
        assertEquals(fetched(contexts(0)).format, format)
        assertEquals(fetched(contexts(0)).typedAs(format), history())
        assertEquals(fetched(contexts(1)).format, TextInteractionFormat)
        assertEquals(fetched(contexts(1)).typedAs(TextInteractionFormat), text)
        assertEquals(fetched(contexts(2)).format, LabeledCheckboxInteractionFormat)
        assertEquals(fetched(contexts(2)).typedAs(LabeledCheckboxInteractionFormat), checkbox)
        intercept[IllegalArgumentException](fetched(contexts(1)).typedAs(LabeledCheckboxInteractionFormat))
      }
    }

    test(s"$storage rejects a payload that disagrees with its declared InteractionFormat and rolls back its batch") {
      val valid = encoded(history())
      val wrongFormat = valid.copy(format = LabeledCheckboxInteractionFormat)
      val request = BulkStoreToDbRequest(Seq(context.toSyncContext("a-valid"), context.toSyncContext("z-invalid")),
        Seq(valid, wrongFormat), config, storage)
      intercept[Exception](storeAllVia(request, logger))
      assertEquals(fetch(storage), Map.empty)
    }

    test(s"$storage replaces the record when the InteractionFormat changes, even with the same state schema") {
      val text = InteractionVariableHistory.empty(TextInteractionFormat).addEvent(
        AnswerChangedEvent[TextInteractionFormat.type]("same JSON schema", time, UpdateImportance.MAJOR))
      val original = StoreToDbRequest(sync, InteractionHistoryJson.fromHistory(TextInteractionFormat, text), config, storage)
      storeVia(original, logger)
      // Both formats encode String/Unit/Unit; their identity must still remain distinct.
      val changed = original.copy(history = original.history.copy(format = SqlCommandExerciseFormat))
      changed.history.toTyped // The replacement payload is valid for its declared format.
      storeVia(changed, logger)
      val fetched = fetchVia(fetchRequest(storage), logger).fetchedElements(sync)
      assertEquals(fetched.format, SqlCommandExerciseFormat)
      assertEquals(fetched.typedAs(SqlCommandExerciseFormat).allStates.map(_.newState), Set("same JSON schema"))
    }
  }

  test("bulk HTTP storage authorizes every context before storing any histories") {
    withHttpCommands() { send =>
      val publicConfig = BackendDatabase
      Seq(BlobFormat, RelationalFormat).foreach { storage =>
        val legitimate = BulkStoreToDbRequest(Seq(sync, context.toSyncContext("other")),
          Seq(encoded(history()), encoded(history("other"))), publicConfig, storage)
        def sendBulk(request: BulkStoreToDbRequest) = send(command(bulkStoreToDbCommand.name, request))
        assert(sendBulk(legitimate).result.isRight)
        val forged = legitimate.copy(syncContexts = Seq(sync, sync.copy(userId = "another-user")),
          histories = Seq(encoded(history("must not be stored")), encoded(history("forged"))))
        assert(sendBulk(forged).result.isLeft)
        assertEquals(read[History](fetch(storage)(sync)), history())
      }
    }
  }

  test("relational events format round-trips current answers, multiple graders and scaffolding") {
    val saved = history()
    store(saved, DatabaseHistoryFormat.RelationalFormat)
    assertEquals(read[History](fetch()(sync)), saved)
    deleteVia(DeleteInDbRequest(context, Some(key), config, DatabaseHistoryFormat.RelationalFormat), logger)
    assertEquals(fetch(), Map.empty)
  }

  test("replayed event IDs are confirmed unchanged and different content under a stored ID is rejected in both layouts") {
    List(BlobFormat, RelationalFormat).foreach { storage =>
      val original = history()
      store(original, storage)
      val restored = read[History](fetch(storage)(sync))
      val replay = storeVia(StoreToDbRequest(sync, encoded(restored), config, storage), logger)
      assertEquals((replay.elementsAdded, replay.elementsUnchanged), (Set.empty[EventId], original.events.ids))
      assertEquals(read[History](fetch(storage)(sync)), original)
      val answer = original.allStates.head
      val revised = answer.copy[format.type](newState = "revised", timestamp = answer.timestamp.plusNanos(1))
      val delta = InteractionVariableHistory(format, InteractionEvents(Set[InteractionEvent[format.type]](revised)))
      val rowId = if storage == RelationalFormat then Some(scalar("SELECT id FROM answer_event")) else None
      intercept[Exception](store(delta, storage))
      assertEquals(read[History](fetch(storage)(sync)), original, "the rejected request is rolled back")
      rowId.foreach { id =>
        assertEquals(scalar("SELECT COUNT(*) FROM answer_event"), 1L)
        assertEquals(scalar("SELECT id FROM answer_event"), id)
      }
      val removed = deleteVia(DeleteInDbRequest(context, Some(key), config, storage), logger)
      assertEquals(removed.elementsRemoved, Set(sync))
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
    assertEquals(restored.gradingRecords.keySet, Set("first", "second").map(restored.referenceOf))
    assert(!fetch()(sync).render().contains("answer_id"))
    assert(!fetch()(sync).render().contains("sync_id"))
  }

  test("current gzip HTTP commands store, fetch, update feedback and delete in both formats") {
    withHttpCommands() { send =>
      List(BlobFormat, RelationalFormat).foreach { format =>
        val graded = history()
        val answerOnly = graded.copy(events = InteractionEvents(graded.allStates.map(event => event: InteractionEvent[TextInputWithLlmGradingAndScaffoldingFormat.type])))
        def save(value: History): Unit = {
          val response = send(storeCommand(StoreToDbRequest(sync, encoded(value), config, format)))
          assert(response.result.isRight, response.result.left.toOption.map(_.msg).getOrElse(""))
        }
        def load(): Map[SyncContext, ujson.Value] =
          send(command(fetchEventsFromDbCommand.name, fetchRequest(format))).throwIfContainsError.result.toOption.get.getAs[DbFetchResponse].fetchedElements.map((key, value) => key -> value.historyJson)
        save(answerOnly)
        assertEquals(read[History](load()(sync)), answerOnly)
        save(graded) // The answer is unchanged: only grading and scaffolding were added.
        save(graded) // Retry must not duplicate feedback rows.
        assertEquals(read[History](load()(sync)), graded)
        if format == RelationalFormat then {
          assertEquals(scalar("SELECT COUNT(*) FROM answer_event"), 1L)
          assertEquals(scalar("SELECT COUNT(*) FROM grading_event"), 2L)
          assertEquals(scalar("SELECT COUNT(*) FROM scaffolding_event"), 1L)
        }
        val rangeRequest = fetchRequest(format, starting = time.plusSeconds(1), until = time.plusSeconds(1))
        val ranged = send(command(fetchEventsFromDbCommand.name, rangeRequest)).throwIfContainsError
          .result.toOption.get.getAs[DbFetchResponse].fetchedElements(sync).typedAs(TextInputWithLlmGradingAndScaffoldingFormat)
        assertEquals(ranged.size, 2)
        assert(ranged.allStates.isEmpty)
        assert(send(command(fetchEventsFromDbCommand.name,
          rangeRequest.copy(usageContext = context.copy(userId = "another-user")))).result.isLeft)
        val deleted = send(command(clearValuesDbCommand.name, DeleteInDbRequest(context, Some(key), config, format)))
        assert(deleted.result.isRight)
        assertEquals(load(), Map.empty)
      }
    }
  }

  test("all-workbook reset deletes only the authenticated user's application histories in every format") {
    val second = context.copy(scenarioId = "second-workbook")
    val otherUser = context.copy(userId = "another-user")
    val otherProgram = context.copy(programId = "another-program")
    withHttpCommands() { send =>
      List(BlobFormat, RelationalFormat).foreach { format =>
        List(context, second, otherUser, otherProgram).foreach { usage =>
          storeVia(StoreToDbRequest(usage.toSyncContext(key), encoded(history()), config, format), logger)
        }
        def readAt(usage: UsageContext) = fetchVia(fetchRequest(format, keys = Seq.empty, usage = usage), logger).fetchedElements
        def erase(usage: UsageContext) = send(command(resetUserHistoriesDbCommand.name,
          DeleteInDbRequest(usage, None, config, format, allWorkbooks = true)))
        val missingScope = send(command(resetUserHistoriesDbCommand.name,
          DeleteInDbRequest(context, None, config, format)))
        assert(missingScope.result.isLeft, "the new reset command requires an explicit full scope")
        assert(readAt(context).nonEmpty)
        assert(erase(otherUser).result.isLeft, "a signed token cannot reset another account")
        def removed(response: ExecutionClientResponse) = response.throwIfContainsError.result.toOption.get.getAs[SyncSuccess[SyncContext]].elementsRemoved
        assertEquals(removed(erase(context)), Set(context.toSyncContext(key), second.toSyncContext(key)))
        assertEquals(readAt(context), Map.empty)
        assertEquals(readAt(second), Map.empty)
        assert(readAt(otherUser).nonEmpty)
        assert(readAt(otherProgram).nonEmpty)
        assertEquals(removed(erase(context)), Set.empty[SyncContext], "reset retries are idempotent")
      }
    }
  }

  test("HTTP command logs persist trusted client IPv4 and IPv6 and reject untrusted forwarded addresses") {
    def loggedAddress(table: String): (String, String) = MysqlTestDatabase.withConnection { connection =>
      Using.resource(connection.createStatement()) { statement =>
        Using.resource(statement.executeQuery(s"SELECT origin,ipAddress FROM `$table` ORDER BY id DESC LIMIT 1")) { rows =>
          assert(rows.next())
          rows.getString(1) -> rows.getString(2)
        }
      }
    }
    List(
      ("127.0.0.1", "198.51.100.42", "198.51.100.42"),
      ("127.0.0.1", "2001:db8::42", java.net.InetAddress.getByName("2001:db8::42").getHostAddress),
      ("192.0.2.1", "198.51.100.99", "127.0.0.1")
    ).foreach { (trusted, forwarded, expected) =>
      withHttpCommands(Some(forwarded), trusted) { send =>
        val fetch = command(fetchEventsFromDbCommand.name, fetchRequest(RelationalFormat, keys = Seq.empty))
        assert(send(fetch).result.isRight)
        assertEquals(loggedAddress("syncedLog"), expected -> expected)
        // Unsupported commands still go through the ordinary command log and error envelope.
        assert(send(ExecutionCommand("ip-log-fixture", ujson.Obj())).result.isLeft)
        assertEquals(loggedAddress("commandLog"), expected -> expected)
      }
    }
  }

  test("large cumulative gzip histories sync repeatedly over HTTP with the same context and different timestamps") {
    withHttpCommands() { send =>
      val first = InteractionVariableState("x" * 350000, UpdateImportance.MAJOR, time)
      val second = InteractionVariableState("second edit", UpdateImportance.MAJOR, time.plusSeconds(1))
      val initial = InteractionVariableHistory.empty(format).addState(first)
      val cumulative = initial.addState(second)
      def request(value: History) = StoreToDbRequest(sync, encoded(value), config, RelationalFormat)
      def sendBounded(value: History): Unit = {
        val response = send(storeCommand(request(value)))
        assert(response.result.isRight)
        assert(response.additionalOutput.loggerOut.length < 16384)
        assert(!response.additionalOutput.loggerOut.contains("x" * 1000))
      }
      sendBounded(initial)
      val syncId = scalar("SELECT id FROM sync_context_id")
      sendBounded(cumulative)
      sendBounded(cumulative)
      assertEquals(scalar("SELECT id FROM sync_context_id"), syncId)
      assertEquals(scalar("SELECT COUNT(*) FROM answer_event"), 2L)
      assertEquals(read[History](fetch()(sync)).historyStates, cumulative.historyStates)
    }
  }

  test("database SQL and authentication failures return useful errors without preventing the next sync") {
    withHttpCommands() { send =>
      val credentials = DatabaseConfigWithCredentials.readFromEnv()
      val request = StoreToDbRequest(sync, encoded(history()), credentials, RelationalFormat)
      val invalid = request.copy(syncContext = context.toSyncContext("oversized-key-" + "x" * 300))
      val error = send(storeCommand(invalid)).result.left.toOption.get
      assert(error.msg.contains("column limits"))
      assert(error.msg.contains("SQLState=22001"))
      assert(error.msg.contains("errorCode=1406"))
      assert(error.cause.exists(_.msg.contains("SQLState=22001")))
      assert(!write(error).contains("Data truncation"))
      assertEquals(scalar("SELECT COUNT(*) FROM sync_context_id"), 0L)
      val auth = send(storeCommand(request.copy(databaseConfig = credentials.copy(password = "incorrect-test-password")))).result.left.toOption.get
      assert(auth.msg.contains("rejected the credentials"))
      assert(auth.msg.contains("SQLState=28000"))
      assert(!auth.msg.contains("incorrect-test-password"))
      assert(send(storeCommand(request)).result.isRight)
      assertEquals(read[History](fetch()(sync)), history())
    }
  }

  test("schema inspection and setup leave existing rows untouched and create nothing on conflict") {
    store(history())
    MysqlTestDatabase.withConnection { connection =>
      assert(DatabaseSchemaInspector.inspect(connection).valid)
      assert(!DatabaseSchemaSetup.setupIfEmpty(connection).created)
      assertEquals(read[History](fetch()(sync)), history())
      MysqlTestDatabase.clearSchema(connection)
      Using.resource(connection.createStatement()) { statement =>
        statement.executeUpdate("CREATE TABLE user (id INT NOT NULL PRIMARY KEY, marker VARCHAR(20))")
        statement.executeUpdate("INSERT INTO user VALUES (1,'untouched')")
        val before = DatabaseSchemaInspector.inspect(connection)
        assert(before.conflicts.nonEmpty)
        val after = DatabaseSchemaSetup.setupIfEmpty(connection)
        assertEquals(after.existingTables, Set("user"))
        assert(!after.created)
        Using.resource(statement.executeQuery("SELECT marker FROM user WHERE id=1")) { rows =>
          assert(rows.next()); assertEquals(rows.getString(1), "untouched")
        }
      }
    }
  }

  test("schema inspection detects collation changes and never repairs them") {
    MysqlTestDatabase.withConnection { connection =>
      Using.resource(connection.createStatement())(_.executeUpdate(
        "ALTER TABLE sync_context_id MODIFY program_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL"))
      val report = DatabaseSchemaInspector.inspect(connection)
      assert(report.conflicts.exists(_.contains("collation")), report.toString)
      assert(!DatabaseSchemaSetup.setupIfEmpty(connection).created)
      assertEquals(DatabaseSchemaInspector.inspect(connection).conflicts, report.conflicts)
    }
  }

  test("the single setup script creates every required table in an empty database") {
    MysqlTestDatabase.withConnection { connection =>
      MysqlTestDatabase.clearSchema(connection)
      Using.resource(connection.createStatement()) { statement =>
        DatabaseSqlScripts.statements.foreach(sql => statement.executeUpdate(sql.toString))
      }
      val report = DatabaseSchemaInspector.inspect(connection)
      assertEquals(report.existingTables, (RelationalFormat.tables ++ BlobFormat.tables).map(_.name).toSet ++ Set("user", "loginTokens", "commandLog", "syncedLog", "submissionLog"))
      assertEquals(report.conflicts, Nil)
      assert(!DatabaseSchemaSetup.setupIfEmpty(connection).created)
    }
  }

  test("schema inspection rejects additional uniqueness that would change answer upsert semantics") {
    store(history())
    MysqlTestDatabase.withConnection { connection =>
      Using.resource(connection.createStatement())(_.executeUpdate("ALTER TABLE answer_event ADD UNIQUE KEY wrong_single_answer (sync_id)"))
      val report = DatabaseSchemaInspector.inspect(connection)
      assert(report.conflicts.exists(_.contains("unexpected uniqueness")), report.toString)
      assert(!DatabaseSchemaSetup.setupIfEmpty(connection).created)
      intercept[IllegalArgumentException](fetch())
      assertEquals(scalar("SELECT COUNT(*) FROM answer_event"), 1L)
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
        DatabaseSchemaSetup.setupIfEmpty(connection)
      }
    })
    val results = Await.result(Future.sequence(attempts), 20.seconds)
    assert(results.forall(_.valid), results.toString)
    assertEquals(results.count(_.created), 1)
  }

  test("context uniqueness preserves case and trailing spaces exactly like Scala strings") {
    val variants = List(context, context.copy(programId = "PROGRAM"), context.copy(programId = "program "))
    variants.foreach { usage =>
      val identity = usage.toSyncContext(key)
      storeVia(StoreToDbRequest(identity, encoded(history()), config, RelationalFormat), logger)
    }
    assertEquals(scalar("SELECT COUNT(*) FROM sync_context_id"), 3L)
    variants.foreach { usage =>
      val response = fetchVia(fetchRequest(RelationalFormat, keys = Seq.empty, usage = usage), logger)
      assertEquals(response.fetchedElements.keySet, Set(usage.toSyncContext(key)))
    }
  }

  for storage <- List(BlobFormat, RelationalFormat) do {
    test(s"$storage merges deltas idempotently and preserves events at identical timestamps") {
      val first = history()
      val second = history("second", time)
      store(first, storage)
      store(second, storage)
      store(first, storage)
      assertEquals(read[History](fetch(storage)(sync)), first.mergeWith(second))
    }

    test(s"$storage range reads retain feedback independently and preserve adjacent nanoseconds") {
      val first = history()
      store(first, storage)
      store(history("second", time.plusNanos(1)), storage)
      def range(from: Instant, until: Instant) = fetchedHistory(fetchRequest(storage, starting = from, until = until))
      assertEquals(range(time, time).events.values, first.allStates.map(event => event: InteractionEvent[format.type]))
      val feedbackTime = time.plusSeconds(1)
      val feedback = range(feedbackTime, feedbackTime)
      assertEquals(feedback.size, 2)
      assert(feedback.allStates.isEmpty)
      assertEquals(feedback.gradingRecords.keySet, Set(feedback.referenceOf("first")))
      assertEquals(range(time.minusNanos(1), time.minusNanos(1)).size, 0)
    }

    test(s"$storage replaces the earlier answers when an exercise changes its format") {
      store(history(), storage)
      val other = LabeledCheckboxInteractionFormat
      val booleanHistory = InteractionVariableHistory.empty(other).addEvent(AnswerChangedEvent[other.type](true, time, UpdateImportance.MAJOR))
      storeVia(StoreToDbRequest(sync, InteractionHistoryJson.fromHistory(other, booleanHistory), config, storage), logger)
      assertEquals(fetchVia(fetchRequest(storage), logger).fetchedElements(sync).typedAs(other), booleanHistory)
    }

    test(s"$storage supports the complete Instant range") {
      val extremes = InteractionVariableHistory.empty(format)
        .addEvent(AnswerChangedEvent[format.type]("minimum", earliestEvent, UpdateImportance.MAJOR))
        .addEvent(AnswerChangedEvent[format.type]("maximum", latestEvent, UpdateImportance.MAJOR))
      store(extremes, storage)
      assertEquals(fetchedHistory(fetchRequest(storage)), extremes)
    }
  }

  test("concurrent blob writers merge under a row lock without losing either delta") {
    import scala.concurrent.{Await, ExecutionContext, Future}
    import scala.concurrent.duration.*
    given ExecutionContext = ExecutionContext.global
    val deltas = (1 to 8).map(index => history(s"answer-$index", time.plusNanos(index)))
    Await.result(Future.traverse(deltas)(value => Future(store(value, BlobFormat))), 30.seconds)
    assertEquals(read[History](fetch(BlobFormat)(sync)), deltas.reduce(_.mergeWith(_)))
  }

  test("relational feedback-only deltas roundtrip without synthesizing answer edits") {
    val original = history()
    val delta = original.copy(events = InteractionEvents(original.events.values -- original.allStates))
    store(delta)
    assertEquals(scalar("SELECT COUNT(*) FROM answer_event"), 0L)
    assertEquals(read[History](fetch()(sync)), delta)
  }

  test("a relational write failure rolls back every event inserted by that request") {
    store(history())
    MysqlTestDatabase.withConnection { connection =>
      Using.resource(connection.createStatement())(_.executeUpdate(
        // Feedback rows refer to their answer, so the constraint rejects the answer's reference.
        s"ALTER TABLE grading_event ADD CONSTRAINT reject_fixture CHECK (LOCATE('${InteractionFormat.referenceOf(format, "reject-fixture").fingerprint}', CAST(serialized_data AS CHAR)) = 0)"))
    }
    intercept[java.sql.SQLException](store(history("reject-fixture", time.plus(java.time.Duration.ofDays(1)))))
    assertEquals(read[History](fetch()(sync)), history())
    assertEquals(scalar("SELECT COUNT(*) FROM answer_event"), 1L)
  }

  for stored <- Seq("not-an-ISO-timestamp", "2026-11-09T12:13:14.123456790", "2026-11-09T12:13:14.1234567890") do
    test(s"invalid timestamp_nano discards only its event and reports the reason: $stored") {
      val original = history()
      val healthy = history("healthy", time.plusSeconds(10))
      val combined = original.mergeWith(healthy)
      store(combined)
      val corruptEventId = original.allStates.head.eventId
      MysqlTestDatabase.withConnection { connection =>
        Using.resource(connection.prepareStatement("UPDATE answer_event SET timestamp_nano=? WHERE event_id=?")) { statement =>
          statement.setString(1, stored)
          statement.setString(2, corruptEventId.value)
          assertEquals(statement.executeUpdate(), 1)
        }
      }
      val readLogger = BasicLogger()
      val fetched = fetchVia(fetchRequest(RelationalFormat, keys = Seq.empty), readLogger)
      val expected = combined.copy(events = InteractionEvents(
        combined.events.values.filterNot(_.eventId == corruptEventId)))
      assertEquals(fetched.fetchedElements(sync).typedAs(format), expected)
      assert(readLogger.getOut().contains(s"Discarded the unreadable answer_event row $corruptEventId"))
      assert(readLogger.getOut().contains("timestamp_nano must match the serialized event"))
      assertEquals(scalar("SELECT COUNT(*) FROM answer_event"), 2L, "reading must not delete stored records")
    }

  test("relational timestamps use indexed ISO 8601 text with nine fractional digits") {
    val exactSecond = time.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
    val oneNano = exactSecond.plusNanos(1)
    val sample = InteractionVariableHistory.empty(format)
      .addEvent(AnswerChangedEvent[format.type]("second", exactSecond, UpdateImportance.MAJOR))
      .addEvent(AnswerChangedEvent[format.type]("one nanosecond", oneNano, UpdateImportance.MAJOR))
    store(sample)
    MysqlTestDatabase.withConnection { connection =>
      Using.resource(connection.createStatement()) { statement =>
        Using.resource(statement.executeQuery("SELECT timestamp_nano FROM answer_event ORDER BY timestamp_nano")) { rows =>
          assert(rows.next())
          assertEquals(rows.getString(1), "2026-11-09T12:13:14.000000000")
          assert(rows.next())
          assertEquals(rows.getString(1), "2026-11-09T12:13:14.000000001")
          assert(!rows.next())
        }
      }
    }
    assertEquals(fetchedHistory(fetchRequest(RelationalFormat, starting = oneNano, until = oneNano)).allStates.map(_.timestamp), Set(oneNano))
  }

  test("ISO signed and expanded years preserve chronological range semantics") {
    val timestamps = Seq(earliestEvent, java.time.LocalDateTime.of(-1, 1, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC), java.time.LocalDateTime.of(0, 1, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC),
      time, java.time.LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999999999).toInstant(java.time.ZoneOffset.UTC), java.time.LocalDateTime.of(10000, 1, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC), latestEvent)
    val sample = timestamps.foldLeft(InteractionVariableHistory.empty(format)) { (history, timestamp) =>
      history.addEvent(AnswerChangedEvent[format.type](timestamp.toString, timestamp, UpdateImportance.MAJOR))
    }
    store(sample)
    val ranges = Seq(time -> Instant.MAX, Instant.MIN -> time,
      java.time.LocalDateTime.of(0, 1, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC) -> java.time.LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999999999).toInstant(java.time.ZoneOffset.UTC),
      java.time.LocalDateTime.of(10000, 1, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC) -> Instant.MAX,
      Instant.MIN -> java.time.LocalDateTime.of(-1, 12, 31, 23, 59).toInstant(java.time.ZoneOffset.UTC), Instant.MIN -> Instant.MAX)
    ranges.foreach { (starting, until) =>
      assertEquals(fetchedHistory(fetchRequest(RelationalFormat, starting = starting, until = until)).allStates.map(_.timestamp),
        timestamps.filter(value => !value.isBefore(starting) && !value.isAfter(until)).toSet)
    }
  }

  test("an unreadable stored record is discarded while the other histories and events still load") {
    val other = context.toSyncContext("other_history")
    Seq(RelationalFormat, BlobFormat).foreach { storage =>
      MysqlTestDatabase.withConnection(MysqlTestDatabase.reset)
      store(history(), storage)
      storeVia(StoreToDbRequest(other, encoded(history("other")), config, storage), logger)
      MysqlTestDatabase.withConnection { connection =>
        Using.resource(connection.createStatement())(_.executeUpdate(
          if storage == BlobFormat then s"UPDATE blob_history SET history_json='{\"broken\": true}' WHERE variable_id='$key'"
          else s"UPDATE answer_event SET serialized_data='{\"broken\": true}' WHERE sync_id=(SELECT id FROM sync_context_id WHERE variable_id='$key')"))
      }
      val fetched = fetchVia(fetchRequest(storage, keys = Seq.empty), logger).fetchedElements
      assertEquals(fetched(other).typedAs(format).allStates, history("other").allStates, storage.toString)
      if storage == BlobFormat then assert(!fetched.contains(sync), "the unreadable blob is discarded")
      else {
        // Only the unreadable answer row is discarded; its gradings and scaffolding remain.
        val remaining = fetched(sync).typedAs(format).events.values
        assert(remaining.nonEmpty && remaining.forall(!_.isInstanceOf[AnswerChangedEvent[?]]), remaining.toString)
      }
    }
  }

  test("a history saved under an unknown format name is discarded, the others still load, and a write replaces it") {
    val other = context.toSyncContext("other_history")
    Seq(RelationalFormat -> "sync_context_id", BlobFormat -> "blob_history").foreach { (storage, table) =>
      MysqlTestDatabase.withConnection(MysqlTestDatabase.reset)
      store(history(), storage)
      storeVia(StoreToDbRequest(other, encoded(history("other")), config, storage), logger)
      MysqlTestDatabase.withConnection { connection =>
        Using.resource(connection.createStatement())(_.executeUpdate(s"UPDATE $table SET format='unknown-format' WHERE variable_id='$key'"))
      }
      val fetched = fetch(storage)
      assertEquals(fetched.keySet, Set(other), storage.toString)
      assertEquals(read[History](fetched(other)).allStates, history("other").allStates)
      // Writing to it replaces the record this version cannot read.
      val later = history("later", time.plus(java.time.Duration.ofDays(1)))
      store(later, storage)
      assertEquals(read[History](fetch(storage)(sync)), later, storage.toString)
    }
  }

  test("relational inclusive ranges preserve adjacent nanoseconds across midnight") {
    val endOfDay = time.atOffset(java.time.ZoneOffset.UTC).toLocalDate.atTime(23, 59, 59, 999999999).toInstant(java.time.ZoneOffset.UTC)
    val nextDay = endOfDay.plusNanos(1)
    val before = history("before midnight", endOfDay)
    val after = history("at midnight", nextDay)
    store(before)
    store(after)
    val request = fetchRequest(RelationalFormat, starting = endOfDay, until = nextDay)
    val fetched = fetchedHistory(request)
    assertEquals(fetched.allStates, before.allStates ++ after.allStates)
    assertEquals(fetched.events.values.size, 2)
    val exactBoundary = request.copy(starting = nextDay)
    val exact = fetchedHistory(exactBoundary)
    assertEquals(exact.allStates, after.allStates)
    assertEquals(exact.events.values.size, 1)
  }


  for storage <- List(BlobFormat, RelationalFormat) do
    test(s"$storage applies latest-event strategies before timestamp ranges") {
      store(history(), storage)
      store(history("newest", time.plus(java.time.Duration.ofDays(1))), storage)
      val request = fetchRequest(storage, starting = time, until = time.plusSeconds(2), strategy = EventSyncStrategy.KEEP_LAST)
      assertEquals(fetchedHistory(request).size, 0)
      val complete = request.copy(starting = Instant.MIN, until = Instant.MAX)
      val selected = fetchedHistory(complete)
      assertEquals(selected.size, 1)
      assertEquals(selected.events.values.map(selected.stateOf), Set(selected.referenceOf("newest")))
    }
  Seq(BlobFormat, RelationalFormat).foreach { storage =>
    test(s"$storage SYNC_MAJOR preserves pending feedback outside the answer window and ignores newer defaults") {
      val pending = ScaffoldingStartedEvent[format.type](InteractionFormat.referenceOf(format, "first"), time.plusSeconds(3), LlmEntity)
      val saved = history().addEvent(pending).addEvent(AnswerChangedEvent[format.type]("", time.plus(java.time.Duration.ofDays(1)), UpdateImportance.DEFAULT))
      store(saved, storage)
      val request = fetchRequest(storage, starting = pending.timestamp, until = pending.timestamp, strategy = EventSyncStrategy.SYNC_MAJOR)
      val received = fetchedHistory(request)
      assertEquals(received.events.values, Set(pending: InteractionEvent[format.type]))
    }
  }

}
