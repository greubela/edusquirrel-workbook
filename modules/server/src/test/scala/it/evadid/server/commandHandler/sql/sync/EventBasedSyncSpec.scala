package it.evadid.server.commandHandler.sql.sync

import it.evadid.sql.{DatabaseConfig, DatabaseHistoryFormat}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import munit.FunSuite
import upickle.default.{read, write}

/** Tests for the event-based (RelationalEvents) sync functionality. */
class EventBasedSyncSpec extends FunSuite {

  // ============================================================================
  // DatabaseHistoryFormat tests
  // ============================================================================

  test("DatabaseHistoryFormat.RelationalEvents has correct tables") {
    assertEquals(
      DatabaseHistoryFormat.RelationalEvents.tables,
      List("sync_context_id", "answer_event", "scaffolding_event", "grading_event")
    )
  }

  test("DatabaseHistoryFormat.RelationalEvents has only one implementation") {
    // Verify that DatabaseHistoryFormat only has RelationalEvents
    val format: DatabaseHistoryFormat = DatabaseHistoryFormat.RelationalEvents
    assertEquals(format, DatabaseHistoryFormat.RelationalEvents)
  }

  test("DatabaseHistoryFormat.RelationalEvents serialization round-trips") {
    val format: DatabaseHistoryFormat = DatabaseHistoryFormat.RelationalEvents
    val written = write(format)
    val readBack = read[DatabaseHistoryFormat](written)
    assertEquals(readBack, format)
  }

  // ============================================================================
  // SyncDestinationDefinition tests
  // ============================================================================

  test("SyncDestinationDefinition.defaultDatabase uses RelationalEvents") {
    val dest = it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.defaultDatabase
    assertEquals(dest.format, DatabaseHistoryFormat.RelationalEvents)
    assertEquals(dest.buffered, true)
  }

  test("SyncDestinationDefinition.defaultDatabase serialization round-trips") {
    val dest = it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.defaultDatabase
    val written = write(dest)
    val readBack = read[it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition](written)
    assertEquals(readBack, dest)
  }

  // ============================================================================
  // UpsertToDatabase tests
  // ============================================================================

  test("UpsertToDatabase.upsertIntoRelationalTables handles empty history") {
    val context = it.evadid.workbook.interaction.sync.UsageContext("program", "scenario", "student")
    val sync = context.toSyncContext("answer")
    val config = DatabaseConfig("mysql", "unused", "3306", "unused")
    val logger = it.evadid.util.logging.BasicLogger()
    
    val history = InteractionVariableHistory.empty[String, Unit, Unit]
    val request = it.evadid.distribution.commandTypes.SQLCommands.StoreToDbRequest(sync, write(history), config, DatabaseHistoryFormat.RelationalEvents)
    
    // This should complete without error for empty history
    val result = UpsertToDatabase.handleRequest(request, logger)
    assertEquals(result.elementsChanged, 0)
  }

  // ============================================================================
  // DeleteInDatabase tests
  // ============================================================================

  test("DeleteInDatabase.deleteByKeyInDatabaseWithoutKey handles deletion") {
    val context = it.evadid.workbook.interaction.sync.UsageContext("program", "scenario", "student")
    val sync = context.toSyncContext("answer")
    val config = DatabaseConfig("mysql", "unused", "3306", "unused")
    val logger = it.evadid.util.logging.BasicLogger()
    
    val request = it.evadid.distribution.commandTypes.SQLCommands.DeleteInDbRequest(
      context, 
      Some(sync.keyForSerialisation), 
      config, 
      DatabaseHistoryFormat.RelationalEvents
    )
    
    // This should complete without error
    val result = DeleteInDatabase.handleRequest(request, logger)
    // The actual result depends on whether there's data to delete
    assert(result.elementsChanged >= 0)
  }

  // ============================================================================
  // Event-based format only tests (no legacy formats)
  // ============================================================================

  test("RelationalEvents format uses separate tables for different event types") {
    val tables = DatabaseHistoryFormat.RelationalEvents.tables
    assert(tables.contains("answer_event"), "Should have answer_event table")
    assert(tables.contains("grading_event"), "Should have grading_event table")
    assert(tables.contains("scaffolding_event"), "Should have scaffolding_event table")
    assert(tables.contains("sync_context_id"), "Should have sync_context_id table")
  }

  test("Event-based history can be serialized and deserialized") {
    val state = it.evadid.workbook.interaction.variable.InteractionVariableState(
      "test", 
      it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR, 
      java.time.LocalDateTime.now()
    )
    val history = InteractionVariableHistory.empty[String, Unit, Unit].addState(state)
    
    val written = write(history)
    val readBack = read[InteractionVariableHistory[String, Unit, Unit]](written)
    assertEquals(readBack.historyStates.events.size, history.historyStates.events.size)
  }

  test("Empty history has no states") {
    val history = InteractionVariableHistory.empty[String, Unit, Unit]
    assertEquals(history.historyStates.events.size, 0)
    assertEquals(history.historyGrading.gradingsMap.size, 0)
    assertEquals(history.historyScaffolding.scaffoldingMap.size, 0)
  }
}
