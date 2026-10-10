package it.evadid.server

import it.evadid.server.commandHandler.sql.DatabaseConfig
import munit.FunSuite
import upickle.default.*

class ConfigurationCodecSpec extends FunSuite {
  test("database settings serialize as values without opening a connection") {
    val value = DatabaseConfig("localhost", "3306", "fixture", "fixture-user", "fixture-password")
    for (decoded <- List(read[DatabaseConfig](write(value)), readBinary[DatabaseConfig](writeBinary(value)))) {
      assertEquals(decoded, value)
      assertEquals(decoded.jdbcUrl, "jdbc:mysql://localhost:3306/fixture")
    }
  }

  test("mail settings have their own default codec") {
    val value = SendMailCommand.MailConfig("fixture@example.com", "fixture-password", "localhost", "587")
    assertEquals(read[SendMailCommand.MailConfig](write(value)), value)
    assertEquals(readBinary[SendMailCommand.MailConfig](writeBinary(value)), value)
  }

  test("database entry sums preserve rich histories and both key variants") {
    import it.evadid.server.commandHandler.sql.sync.RichDatabaseEntry
    import it.evadid.workbook.interaction.sync.{SyncFormatter, UpdateImportance, UsageContext}
    import it.evadid.workbook.interaction.variable.{InteractionVariableHistorySerialized, InteractionVariableStateSerialized}
    val context = UsageContext("program", "scenario", "fixture")
    val formatter = SyncFormatter.RichInteractionVariableFormatter()
    val history = InteractionVariableHistorySerialized(Set(InteractionVariableStateSerialized("学校", UpdateImportance.DEFAULT,
      java.time.LocalDateTime.parse("2025-01-02T03:04:05"))))
    val serialized = formatter.serialize(context.toSyncContext("original-key"), history)
    val values = List(RichDatabaseEntry.withoutKey(context, formatter, List("event", serialized)),
      RichDatabaseEntry.withKey(context, formatter, List("event", "explicit-key", serialized)))
    values.foreach { value =>
      for (decoded <- List(read[RichDatabaseEntry](write(value)), readBinary[RichDatabaseEntry](writeBinary(value)))) {
        assertEquals(decoded, value)
        assertEquals(decoded.syncContext, value.syncContext)
        assertEquals(decoded.richHistory.fullHistory, history)
      }
    }
  }
}
