package it.evadid.server.commandHandler.sql.sync

import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition
import munit.FunSuite
import upickle.default.*

class HistoryFormatSpec extends FunSuite {
  test("history SQL parameters accept only strings and integer identifiers or timestamps") {
    assert(scala.compiletime.testing.typeChecks("""
      abstract class Probe(connection: java.sql.Connection, logger: it.evadid.util.logging.Logger)
          extends it.evadid.server.commandHandler.sql.sync.HistorySql(connection, logger) {
        def accepted: Int = update("SELECT ?, ?", "key", 123456789L)
      }
    """))
    assert(!scala.compiletime.testing.typeChecks("""
      abstract class Probe(connection: java.sql.Connection, logger: it.evadid.util.logging.Logger)
          extends it.evadid.server.commandHandler.sql.sync.HistorySql(connection, logger) {
        def rejected: Int = update("SELECT ?", new Object)
      }
    """))
  }

  test("both current storage formats roundtrip with their table sets") {
    List(DatabaseHistoryFormat.BlobFormat, DatabaseHistoryFormat.RelationalFormat).foreach { format =>
      assertEquals(read[DatabaseHistoryFormat](write[DatabaseHistoryFormat](format)), format)
    }
    assertEquals(DatabaseHistoryFormat.BlobFormat.tables.map(_.name), Seq("blob_history"))
    assertEquals(DatabaseHistoryFormat.RelationalFormat.tables.map(_.name).toSet, Set("sync_context_id", "answer_event", "grading_event", "scaffolding_event"))
    assertEquals(SyncDestinationDefinition.defaultDatabase.format, DatabaseHistoryFormat.RelationalFormat)
    intercept[Exception](read[DatabaseHistoryFormat]("{\"$type\":\"DatabaseFormatV2\"}"))
  }
}
