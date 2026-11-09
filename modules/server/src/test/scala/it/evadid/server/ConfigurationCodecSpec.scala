package it.evadid.server

import it.evadid.sql.DatabaseConfiguration
import munit.FunSuite
import upickle.default.*

class ConfigurationCodecSpec extends FunSuite {
  test("database settings serialize as values without opening a connection") {
    val value = DatabaseConfiguration("mysql", "localhost", "3306", "fixture", "fixture-user", "fixture-password")
    for (decoded <- List(read[DatabaseConfiguration](write(value)), readBinary[DatabaseConfiguration](writeBinary(value)))) {
      assertEquals(decoded, value)
      assertEquals(decoded.jdbcUrl, "jdbc:mysql://localhost:3306/fixture")
    }
  }

  test("custom history targets require matching session credentials") {
    import it.evadid.distribution.commandTypes.SQLCommands.*
    import it.evadid.server.database.DatabaseRequestConnection
    import it.evadid.sql.DatabaseConfiguration.PartialDatabaseInformation
    import it.evadid.sql.DatabaseHistoryFormat.ClassicJsonWithKey
    import it.evadid.workbook.interaction.sync.UsageContext
    val defaults = DatabaseConfiguration("mysql", "backend", "3306", "main", "server", "server-secret")
    val custom = DatabaseConfiguration("mysql", "custom", "3306", "history", "owner", "session-secret")
    val request = FetchAllFromDbRequest(UsageContext("p", "s", "u"), PartialDatabaseInformation(), None, ClassicJsonWithKey)
    assertEquals(DatabaseRequestConnection.resolve(request, defaults), defaults)
    intercept[IllegalArgumentException](DatabaseRequestConnection.resolve(request.copy(databaseConfig = custom.publicInformation), defaults))
    assertEquals(DatabaseRequestConnection.resolve(request.copy(databaseConfig = custom.publicInformation, databaseCredentials = Some(custom)), defaults), custom)
    intercept[IllegalArgumentException](DatabaseRequestConnection.resolve(request.copy(databaseConfig = defaults.publicInformation, databaseCredentials = Some(custom)), defaults))
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
