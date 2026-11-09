package it.evadid.server

import it.evadid.sql.DatabaseConfig
import munit.FunSuite
import upickle.default.*

class ConfigurationCodecSpec extends FunSuite {
  test("database settings serialize as values without opening a connection") {
    val value = DatabaseConfig("mysql", "localhost", "3306", "fixture", "fixture-user", "fixture-password")
    for (decoded <- List(read[DatabaseConfig](write(value)), readBinary[DatabaseConfig](writeBinary(value)))) {
      assertEquals(decoded, value)
      assertEquals(decoded.jdbcUrl, "jdbc:mysql://localhost:3306/fixture")
    }
  }

  test("history requests use the requested database with the MySQL JDBC protocol") {
    import it.evadid.distribution.commandTypes.SQLCommands.*
    import it.evadid.server.commandHandler.sql.sync.{DeleteInDatabase, FetchFromDatabase, UpsertToDatabase}
    import it.evadid.sql.DatabaseHistoryFormat.ClassicJsonWithKey
    import it.evadid.util.logging.BasicLogger
    import it.evadid.workbook.interaction.sync.UsageContext
    import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
    import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}
    import java.time.LocalDateTime
    import java.sql.{Connection, Driver, DriverManager, DriverPropertyInfo}

    val database = "history_connection_fixture"
    val stopped = new UnsupportedOperationException("Connection intercepted before database access")
    val driver = new Driver {
      override def acceptsURL(url: String): Boolean = url.contains(database)
      override def connect(url: String, info: java.util.Properties): Connection = {
        if !acceptsURL(url) then null else {
          assert(url.startsWith("jdbc:mysql://"), url)
          assert(url.endsWith("/" + database), url)
          throw stopped
        }
      }
      override def getPropertyInfo(url: String, info: java.util.Properties): Array[DriverPropertyInfo] = Array.empty
      override def getMajorVersion: Int = 1
      override def getMinorVersion: Int = 0
      override def jdbcCompliant: Boolean = false
      override def getParentLogger: java.util.logging.Logger = java.util.logging.Logger.getGlobal
    }
    val context = UsageContext("program", "scenario", "user")
    val logger = BasicLogger()
    val history = write(InteractionVariableHistory.empty[String, String, String]
      .addState(InteractionVariableState("answer", MAJOR, LocalDateTime.of(2026, 1, 1, 0, 0))))
    val requests: List[() => Any] = List(
      () => FetchFromDatabase.handleRequest(FetchAllFromDbRequest(context, database, None, ClassicJsonWithKey), logger),
      () => UpsertToDatabase.handleRequest(StoreToDbRequest(context.toSyncContext("answer"), history, database, ClassicJsonWithKey), logger),
      () => DeleteInDatabase.handleRequest(DeleteInDbRequest(context, None, database, ClassicJsonWithKey), logger)
    )
    DriverManager.registerDriver(driver)
    try requests.foreach { request =>
      assert(intercept[UnsupportedOperationException](request()) eq stopped)
    }
    finally DriverManager.deregisterDriver(driver)
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
