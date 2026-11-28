package it.evadid.server

import it.evadid.sql.BackendDatabase
import it.evadid.sql.{DatabaseConfiguration, DatabaseConfigWithCredentials}
import munit.FunSuite
import upickle.default.*

class ConfigurationCodecSpec extends FunSuite {
  test("database settings serialize as values without opening a connection") {
    val value: DatabaseConfiguration = DatabaseConfigWithCredentials("mysql", "localhost", "3306", "fixture", "fixture-user", "fixture-password")
    for (decoded <- List(read[DatabaseConfiguration](write(value)), readBinary[DatabaseConfiguration](writeBinary(value)))) {
      assertEquals(decoded, value)
      decoded match {
        case connection: it.evadid.sql.DatabaseConnection => assertEquals(connection.jdbcUrl, "jdbc:mysql://localhost:3306/fixture")
        case _ => fail("Expected a physical database connection")
      }
    }
  }

  test("backend accepts the current browser sync wire format and rejects the removed partial format") {
    import it.evadid.distribution.commandTypes.SQLCommands.FetchEventsFromDbRequest
    import it.evadid.sql.DatabaseConfig
    val json = ujson.read("""{"usageContext":{"programId":"edusquirrel","scenarioId":"workbookTest","userId":"student"},
      "databaseConfig":"BackendDatabase",
      "keys":[],"starting":"2026-01-01T00:00:00Z","until":"2026-12-31T00:00:00Z","databaseFormat":"RelationalFormat","strategy":"SYNC_EVERYTHING"}""")
    assertEquals(read[FetchEventsFromDbRequest](json).databaseConfig, BackendDatabase)
    val unsupported = ujson.read("""{"databaseOp":"db_332371_12","dbFormatOp":"mysql","hostOp":"herrgreubel.lima-db.de","portOp":"3306"}""")
    json("databaseConfig") = unsupported
    assert(scala.util.Try(read[FetchEventsFromDbRequest](json)).isFailure)
  }

  test("backend database references resolve the receiving server's environment without exposing its address") {
    import it.evadid.server.database.history.HistoryDatabase
    val configured = DatabaseConfigWithCredentials("mysql", "127.0.0.1", "3306", "edusquirrel", "local-user", "private-password")
    assertEquals(HistoryDatabase.resolve(BackendDatabase, configured), configured)
    assertEquals(read[DatabaseConfiguration](write[DatabaseConfiguration](BackendDatabase)), BackendDatabase)
    assertEquals(BackendDatabase.withoutCredentials, BackendDatabase)
    assert(!write[DatabaseConfiguration](BackendDatabase).contains("private-password"))
  }

  test("public targets resolve server credentials; supplied credentials bypass server defaults") {
    import it.evadid.server.database.history.HistoryDatabase
    val defaults = DatabaseConfigWithCredentials("mysql", "backend", "3306", "main", "server", "server-secret")
    val custom = DatabaseConfigWithCredentials("mysql", "custom", "3306", "history", "owner", "session-secret")
    assertEquals(HistoryDatabase.resolve(defaults.withoutCredentials, defaults), defaults)
    intercept[IllegalArgumentException](HistoryDatabase.resolve(custom.withoutCredentials, defaults))
    assertEquals(HistoryDatabase.resolve(custom, throw AssertionError("Must not read default credentials")), custom)
    List[DatabaseConfiguration](defaults.withoutCredentials, custom).foreach { config =>
      assertEquals(read[DatabaseConfiguration](write(config)), config)
    }
  }

  test("partial settings name the missing variable without exposing secrets; complete groups are optional") {
    val mail = Map("MAIL_EMAIL" -> "fixture@example.invalid", "MAIL_PW" -> "private-mail-password")
    List("MAIL_EMAIL", "MAIL_PW").foreach { missing =>
      val error = intercept[IllegalStateException](TestServerConfig((mail - missing).toSeq*))
      assert(error.getMessage.contains(missing))
      assert(!error.getMessage.contains("private-mail-password"))
    }
    val settings = TestServerConfig(mail.toSeq*).mail.get
    assertEquals((settings.host, settings.port), ("mail.lima-city.de", "587"))
    assert(!settings.toString.contains("private-mail-password"))
    assertEquals(TestServerConfig().mail, None)
    assertEquals(TestServerConfig().database, None)
    val database = intercept[IllegalStateException](TestServerConfig("SQL_HOST" -> "localhost"))
    assert(database.getMessage.contains("SQL_PORT"))
    intercept[IllegalStateException](TestServerConfig("OPENAI_MAX_TOKENS" -> "many"))
  }

  test("the server refuses to start without a token signing secret") {
    val error = intercept[IllegalStateException](ServerConfig.from(Map.empty[String, String].get))
    assert(error.getMessage.contains("SERVER_SECRET"))
    intercept[IllegalArgumentException](it.evadid.server.account.SignedTokens("  "))
  }

  test("the backend database is assembled from all SQL settings and never printed with its password") {
    val config = TestServerConfig("SQL_HOST" -> "localhost", "SQL_PORT" -> "3306", "SQL_DATABASE" -> "main",
      "SQL_USER" -> "server", "SQL_PW" -> "private-db-password")
    assertEquals(config.backendDatabase, DatabaseConfigWithCredentials("mysql", "localhost", "3306", "main", "server", "private-db-password"))
    assert(!config.toString.contains("private-db-password"))
    assert(intercept[IllegalStateException](TestServerConfig().backendDatabase).getMessage.contains("SQL_HOST"))
  }
}
