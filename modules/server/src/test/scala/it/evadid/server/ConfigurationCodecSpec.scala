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

  test("mail settings have their own default codec") {
    val value = SendMailCommand.MailConfig("fixture@example.com", "fixture-password", "localhost", "587")
    assertEquals(read[SendMailCommand.MailConfig](write(value)), value)
    assertEquals(readBinary[SendMailCommand.MailConfig](writeBinary(value)), value)
  }

  test("database entry sums preserve history JSON and both key variants") {
    import it.evadid.server.commandHandler.sql.sync.RichDatabaseEntry
    import it.evadid.workbook.interaction.sync.UsageContext
    val context = UsageContext("program", "scenario", "fixture")
    val history = """{"keyForSerialisation":"original-key","value":"学校"}"""
    val values = List(RichDatabaseEntry.withoutKey(context, List("event", history)),
      RichDatabaseEntry.withKey(context, List("event", "explicit-key", history)))
    values.foreach { value =>
      for (decoded <- List(read[RichDatabaseEntry](write(value)), readBinary[RichDatabaseEntry](writeBinary(value)))) {
        assertEquals(decoded, value)
        assertEquals(decoded.syncContext, value.syncContext)
        assertEquals(decoded.historyJson, history)
      }
    }
  }
}
