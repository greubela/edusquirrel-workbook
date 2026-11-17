package it.evadid.server

import it.evadid.sql.{DatabaseConfiguration, DatabaseConfigWithCredentials}
import munit.FunSuite
import upickle.default.*

class ConfigurationCodecSpec extends FunSuite {
  test("database settings serialize as values without opening a connection") {
    val value: DatabaseConfiguration = DatabaseConfigWithCredentials("mysql", "localhost", "3306", "fixture", "fixture-user", "fixture-password")
    for (decoded <- List(read[DatabaseConfiguration](write(value)), readBinary[DatabaseConfiguration](writeBinary(value)))) {
      assertEquals(decoded, value)
      assertEquals(decoded.jdbcUrl, "jdbc:mysql://localhost:3306/fixture")
    }
  }

  test("backend accepts the current browser sync wire format and rejects the removed partial format") {
    import it.evadid.distribution.commandTypes.SQLCommands.FetchEventsFromDbRequest
    import it.evadid.sql.DatabaseConfig
    val json = ujson.read("""{"usageContext":{"programId":"edusquirrel","scenarioId":"workbookTest","userId":"student"},
      "databaseConfig":{"$type":"DatabaseConfig","dbFormat":"mysql","host":"herrgreubel.lima-db.de","port":"3306","database":"db_332371_12"},
      "keys":[],"starting":"2026-01-01T00:00","until":"2026-12-31T00:00","databaseFormat":"RelationalFormat","strategy":"SYNC_EVERYTHING"}""")
    assertEquals(read[FetchEventsFromDbRequest](json).databaseConfig, DatabaseConfig.defaultDb)
    val unsupported = ujson.read("""{"databaseOp":"db_332371_12","dbFormatOp":"mysql","hostOp":"herrgreubel.lima-db.de","portOp":"3306"}""")
    json("databaseConfig") = unsupported
    assert(scala.util.Try(read[FetchEventsFromDbRequest](json)).isFailure)
  }

  test("public targets resolve server credentials; supplied credentials bypass server defaults") {
    import it.evadid.server.database.DatabaseRequestConnection
    val defaults = DatabaseConfigWithCredentials("mysql", "backend", "3306", "main", "server", "server-secret")
    val custom = DatabaseConfigWithCredentials("mysql", "custom", "3306", "history", "owner", "session-secret")
    assertEquals(DatabaseRequestConnection.resolve(defaults.withoutCredentials, defaults), defaults)
    intercept[IllegalArgumentException](DatabaseRequestConnection.resolve(custom.withoutCredentials, defaults))
    assertEquals(DatabaseRequestConnection.resolve(custom, throw AssertionError("Must not read default credentials")), custom)
    List[DatabaseConfiguration](defaults.withoutCredentials, custom).foreach { config =>
      assertEquals(read[DatabaseConfiguration](write(config)), config)
    }
  }

  test("missing SMTP credentials report configuration names without exposing secrets") {
    val configured = Map("MAIL_EMAIL" -> "fixture@example.invalid", "MAIL_PW" -> "private-mail-password")
    List("MAIL_EMAIL", "MAIL_PW").foreach { missing =>
      val error = intercept[IllegalStateException](SendMailCommand.readMailConfig((configured - missing).get))
      assert(error.getMessage.contains(missing))
      assert(!error.getMessage.contains("private-mail-password"))
    }
    val settings = SendMailCommand.readMailConfig(configured.get)
    assertEquals(settings.host, "smtp.lima-city.de")
    assertEquals(settings.port, "587")
  }

  test("mail settings have their own default codec") {
    val value = SendMailCommand.MailConfig("fixture@example.com", "fixture-password", "localhost", "587")
    assertEquals(read[SendMailCommand.MailConfig](write(value)), value)
    assertEquals(readBinary[SendMailCommand.MailConfig](writeBinary(value)), value)
  }

}
