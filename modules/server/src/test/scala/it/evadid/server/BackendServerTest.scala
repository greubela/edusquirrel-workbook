package it.evadid.server

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.SQLCommands.{ClearUsageInDbRequest, FetchFromDbRequest, StoreToDbRequest}
import it.evadid.util.Logger
import munit.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class BackendServerTest extends FunSuite {

  test("BackendCommandHandler rejects empty command name") {
    val command = ExecutionCommand("   ", Map.empty)

    intercept[IllegalArgumentException] {
      Await.result(BackendCommandHandler.handleExecution(command, Logger()), 5.seconds)
    }
  }

  test("sync-to-db reads trimmed database credentials from environment") {
    val env = Map(
      "SQL_HOST" -> " db.example.test ",
      "SQL_PORT" -> " 5432 ",
      "SQL_DATABASE" -> " workbook ",
      "SQL_USER" -> " server_user ",
      "SQL_PW" -> " server_password "
    )

    val config = HandleSQLCommand.readDatabaseConfig(env.get)

    assertEquals(config.host, "db.example.test")
    assertEquals(config.port, "5432")
    assertEquals(config.database, "workbook")
    assertEquals(config.user, "server_user")
    assertEquals(config.password, "server_password")
    assertEquals(config.jdbcUrl, "jdbc:mysql://db.example.test:5432/workbook")
  }

  test("sync-to-db fails fast when a required credential is missing") {
    val env = Map(
      "SQL_HOST" -> "db.example.test",
      "SQL_PORT" -> "5432",
      "SQL_DATABASE" -> "workbook",
      "SQL_USER" -> "server_user"
    )

    val error = intercept[IllegalStateException] {
      HandleSQLCommand.readDatabaseConfig(env.get)
    }

    assertEquals(error.getMessage, "SQL_PW is not configured")
  }

  test("sync-to-db passes database config and request to the upsert executor".ignore) {
    val env = Map(
      "SQL_HOST" -> "db.example.test",
      "SQL_PORT" -> "5432",
      "SQL_DATABASE" -> "workbook",
      "SQL_USER" -> "server_user",
      "SQL_PW" -> "server_password"
    )
    val request = StoreToDbRequest(
      programId = "program-1",
      scenarioId = "scenario-1",
      userId = "user-1",
      keyId = "answer-1",
      eventTime = "2026-06-10T12:34:56",
      eventData = "{\"answer\":42}"
    )

    var observedConfig: Option[HandleSQLCommand.DatabaseConfig] = None
    var observedRequest: Option[StoreToDbRequest] = None
    val executor = new HandleSQLCommand.SyncDbExecutor {
      override def upsert(config: HandleSQLCommand.DatabaseConfig, request: StoreToDbRequest, logger: Logger): Int = {
        observedConfig = Some(config)
        observedRequest = Some(request)
        1
      }

      override def fetch(config: HandleSQLCommand.DatabaseConfig, request: FetchFromDbRequest, logger: Logger): Map[String, String] = Map.empty

      override def clear(config: HandleSQLCommand.DatabaseConfig, request: ClearUsageInDbRequest, logger: Logger): Int = 0
    }

    val response = HandleSQLCommand.syncToDb(request, Logger(), env.get, executor)

    assertEquals(response.rowsAffected, 1)
    assertEquals(observedConfig.map(_.jdbcUrl), Some("jdbc:mysql://db.example.test:5432/workbook"))
    assertEquals(observedConfig.map(_.user), Some("server_user"))
    assertEquals(observedConfig.map(_.password), Some("server_password"))
    assertEquals(observedRequest, Some(request))
  }

  test("fetch-from-db passes database config and request to the fetch executor") {
    val env = Map(
      "SQL_HOST" -> "db.example.test",
      "SQL_PORT" -> "5432",
      "SQL_DATABASE" -> "workbook",
      "SQL_USER" -> "server_user",
      "SQL_PW" -> "server_password"
    )
    val request = FetchFromDbRequest(
      programId = "program-1",
      scenarioId = "scenario-1",
      userId = "user-1",
      keyId = Some("answer-1")
    )

    var observedConfig: Option[HandleSQLCommand.DatabaseConfig] = None
    var observedRequest: Option[FetchFromDbRequest] = None
    val executor = new HandleSQLCommand.SyncDbExecutor {
      override def upsert(config: HandleSQLCommand.DatabaseConfig, request: StoreToDbRequest, logger: Logger): Int = 0

      override def fetch(config: HandleSQLCommand.DatabaseConfig, request: FetchFromDbRequest, logger: Logger): Map[String, String] = {
        observedConfig = Some(config)
        observedRequest = Some(request)
        Map("answer-1" -> "stored-history")
      }

      override def clear(config: HandleSQLCommand.DatabaseConfig, request: ClearUsageInDbRequest, logger: Logger): Int = 0
    }

    val response = HandleSQLCommand.fetchFromDb(request, Logger(), env.get, executor)

    assertEquals(response.values, Map("answer-1" -> "stored-history"))
    assertEquals(observedConfig.map(_.jdbcUrl), Some("jdbc:mysql://db.example.test:5432/workbook"))
    assertEquals(observedRequest, Some(request))
  }


  test("clear-db passes database config and request to the clear executor") {
    val env = Map(
      "SQL_HOST" -> "db.example.test",
      "SQL_PORT" -> "5432",
      "SQL_DATABASE" -> "workbook",
      "SQL_USER" -> "server_user",
      "SQL_PW" -> "server_password"
    )
    val request = ClearUsageInDbRequest(
      programId = "program-1",
      scenarioId = "scenario-1",
      userId = "user-1",
      keyId = None
    )

    var observedConfig: Option[HandleSQLCommand.DatabaseConfig] = None
    var observedRequest: Option[ClearUsageInDbRequest] = None
    val executor = new HandleSQLCommand.SyncDbExecutor {
      override def upsert(config: HandleSQLCommand.DatabaseConfig, request: StoreToDbRequest, logger: Logger): Int = 0

      override def fetch(config: HandleSQLCommand.DatabaseConfig, request: FetchFromDbRequest, logger: Logger): Map[String, String] = Map.empty

      override def clear(config: HandleSQLCommand.DatabaseConfig, request: ClearUsageInDbRequest, logger: Logger): Int = {
        observedConfig = Some(config)
        observedRequest = Some(request)
        2
      }
    }

    val response = HandleSQLCommand.clearDb(request, Logger(), env.get, executor)

    assertEquals(response.rowsAffected, 2)
    assertEquals(observedConfig.map(_.jdbcUrl), Some("jdbc:mysql://db.example.test:5432/workbook"))
    assertEquals(observedRequest, Some(request))
  }

}
