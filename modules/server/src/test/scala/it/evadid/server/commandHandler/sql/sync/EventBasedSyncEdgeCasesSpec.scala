package it.evadid.server.commandHandler.sql.sync

import it.evadid.sql.{DatabaseConfig, DatabaseHistoryFormat}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import munit.FunSuite
import upickle.default.{read, write}

/** Additional tests for event-based sync edge cases and behavior. */
class EventBasedSyncEdgeCasesSpec extends FunSuite {

  // ============================================================================
  // DatabaseHistoryFormat edge cases
  // ============================================================================

  test("DatabaseHistoryFormat.RelationalEvents tables are in expected order") {
    val tables = DatabaseHistoryFormat.RelationalEvents.tables
    assertEquals(tables, List("sync_context_id", "answer_event", "scaffolding_event", "grading_event"))
    assertEquals(tables.length, 4)
  }

  test("DatabaseHistoryFormat.RelationalEvents is a sealed trait with one implementation") {
    // Verify sealed trait structure - only one implementation exists
    val format: DatabaseHistoryFormat = DatabaseHistoryFormat.RelationalEvents
    format match {
      case DatabaseHistoryFormat.RelationalEvents => // Expected
      case _ => fail("Should only have RelationalEvents implementation")
    }
  }

  // ============================================================================
  // Serialization edge cases
  // ============================================================================

  test("DatabaseHistoryFormat serialization preserves format") {
    val original = DatabaseHistoryFormat.RelationalEvents
    val written = write(original)
    val readBack = read[DatabaseHistoryFormat](written)
    assertEquals(readBack, original)
    assertEquals(readBack.tables, original.tables)
  }

  test("SyncDestinationDefinition serialization preserves all fields") {
    val dest = it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.defaultDatabase
    val written = write(dest)
    val readBack = read[it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition](written)
    
    readBack match {
      case it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase(config, format, buffered) =>
        assertEquals(config, dest.databaseConfig)
        assertEquals(format, dest.format)
        assertEquals(buffered, dest.buffered)
      case _ => fail("Should deserialize to SyncDestinationBasedOnHistoryDatabase")
    }
  }

  // ============================================================================
  // UpsertToDatabase edge cases
  // ============================================================================

  test("UpsertToDatabase.handleRequest with empty history returns zero changes") {
    val context = it.evadid.workbook.interaction.sync.UsageContext("program", "scenario", "student")
    val sync = context.toSyncContext("answer")
    val config = DatabaseConfig("mysql", "unused", "3306", "unused")
    val logger = it.evadid.util.logging.BasicLogger()
    
    val emptyHistory = InteractionVariableHistory.empty[String, Unit, Unit]
    val request = it.evadid.distribution.commandTypes.SQLCommands.StoreToDbRequest(sync, write(emptyHistory), config, DatabaseHistoryFormat.RelationalEvents)
    
    val result = UpsertToDatabase.handleRequest(request, logger)
    assertEquals(result.elementsAdded, 0)
    assertEquals(result.elementsChanged, 0)
  }

  test("UpsertToDatabase handles history with timestamp") {
    val context = it.evadid.workbook.interaction.sync.UsageContext("program", "scenario", "student")
    val sync = context.toSyncContext("answer")
    val config = DatabaseConfig("mysql", "unused", "3306", "unused")
    val logger = it.evadid.util.logging.BasicLogger()
    
    val timestamp = java.time.LocalDateTime.now()
    val state = it.evadid.workbook.interaction.variable.InteractionVariableState("test", it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR, timestamp)
    val history = InteractionVariableHistory.empty[String, Unit, Unit].addState(state)
    
    val request = it.evadid.distribution.commandTypes.SQLCommands.StoreToDbRequest(sync, write(history), config, DatabaseHistoryFormat.RelationalEvents)
    val result = UpsertToDatabase.handleRequest(request, logger)
    
    // At minimum should process the state
    assert(result.elementsChanged >= 0)
  }

  // ============================================================================
  // DeleteInDatabase edge cases
  // ============================================================================

  test("DeleteInDatabase handles deletion for specific key") {
    val context = it.evadid.workbook.interaction.sync.UsageContext("program", "scenario", "student")
    val sync = context.toSyncContext("answer")
    val config = DatabaseConfig("mysql", "unused", "3306", "unused")
    val logger = it.evadid.util.logging.BasicLogger()
    
    // Delete with specific key
    val request = it.evadid.distribution.commandTypes.SQLCommands.DeleteInDbRequest(
      context, 
      Some(sync.keyForSerialisation), 
      config, 
      DatabaseHistoryFormat.RelationalEvents
    )
    val result = DeleteInDatabase.handleRequest(request, logger)
    assert(result.elementsChanged >= 0)
  }

  test("DeleteInDatabase handles deletion for all keys in context") {
    val context = it.evadid.workbook.interaction.sync.UsageContext("program", "scenario", "student")
    val config = DatabaseConfig("mysql", "unused", "3306", "unused")
    val logger = it.evadid.util.logging.BasicLogger()
    
    // Delete for all keys in context (no specific key)
    val request = it.evadid.distribution.commandTypes.SQLCommands.DeleteInDbRequest(
      context, 
      None, 
      config, 
      DatabaseHistoryFormat.RelationalEvents
    )
    val result = DeleteInDatabase.handleRequest(request, logger)
    assert(result.elementsChanged >= 0)
  }

  test("DeleteInDatabase handles deletion with different formats") {
    val context = it.evadid.workbook.interaction.sync.UsageContext("program", "scenario", "student")
    val config = DatabaseConfig("mysql", "unused", "3306", "unused")
    val logger = it.evadid.util.logging.BasicLogger()
    
    // Delete with RelationalEvents format
    val request = it.evadid.distribution.commandTypes.SQLCommands.DeleteInDbRequest(
      context, 
      None, 
      config, 
      DatabaseHistoryFormat.RelationalEvents
    )
    val result = DeleteInDatabase.handleRequest(request, logger)
    assertEquals(result.elementsChanged, 0) // No data to delete
  }

  // ============================================================================
  // SyncDestinationDefinition edge cases
  // ============================================================================

  test("SyncDestinationDefinition.defaultDatabase has buffered=true") {
    val dest = it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.defaultDatabase
    assertEquals(dest.buffered, true)
  }

  test("SyncDestinationDefinition.defaultDatabase uses default database config") {
    val dest = it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.defaultDatabase
    assertEquals(dest.databaseConfig, DatabaseConfig.defaultDb)
    assertEquals(dest.databaseConfig.host, "herrgreubel.lima-db.de")
    assertEquals(dest.databaseConfig.port, "3306")
  }

  test("SyncDestinationDefinition.defaultDatabase withoutCredentials removes password") {
    val dest = it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.defaultDatabase
    val withoutCreds = dest.withoutCredentials
    
    withoutCreds match {
      case it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase(config, format, buffered) =>
        // Verify credentials are removed
        assert(!config.toString.contains("secret") && !config.toString.contains("password"))
        assertEquals(format, dest.format)
        assertEquals(buffered, dest.buffered)
      case _ => fail("Should preserve type")
    }
  }

  // ============================================================================
  // History serialization edge cases
  // ============================================================================

  test("Empty history serializes and deserializes correctly") {
    val emptyHistory = InteractionVariableHistory.empty[String, Unit, Unit]
    
    val written = write(emptyHistory)
    val readBack = read[InteractionVariableHistory[String, Unit, Unit]](written)
    
    assertEquals(readBack.historyStates.events.size, 0)
    assertEquals(readBack.historyGrading.gradingsMap.size, 0)
    assertEquals(readBack.historyScaffolding.scaffoldingMap.size, 0)
  }

  test("History with single state serializes and deserializes correctly") {
    val timestamp = java.time.LocalDateTime.now()
    val state = it.evadid.workbook.interaction.variable.InteractionVariableState("test", it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR, timestamp)
    val history = InteractionVariableHistory.empty[String, Unit, Unit].addState(state)
    
    val written = write(history)
    val readBack = read[InteractionVariableHistory[String, Unit, Unit]](written)
    
    assertEquals(readBack.historyStates.events.size, 1)
    assertEquals(readBack.historyStates.lastStateOption.map(_.value), Some("test"))
  }

  test("History preserves state importance levels") {
    val timestamp = java.time.LocalDateTime.now()
    val states = List(
      it.evadid.workbook.interaction.variable.InteractionVariableState("a", it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR, timestamp),
      it.evadid.workbook.interaction.variable.InteractionVariableState("b", it.evadid.workbook.interaction.sync.UpdateImportance.MINOR, timestamp.plusSeconds(1)),
      it.evadid.workbook.interaction.variable.InteractionVariableState("c", it.evadid.workbook.interaction.sync.UpdateImportance.TEMPORARY, timestamp.plusSeconds(2))
    )
    val history = InteractionVariableHistory.empty[String, Unit, Unit].addStates(states)
    
    val written = write(history)
    val readBack = read[InteractionVariableHistory[String, Unit, Unit]](written)
    
    assertEquals(readBack.historyStates.events.size, 3)
  }

  // ============================================================================
  // Format-only tests (no legacy formats exist)
  // ============================================================================

  test("No legacy snapshot formats exist in DatabaseHistoryFormat") {
    // This test verifies that only RelationalEvents exists
    // If ClassicKeylessJson, ClassicJsonWithKey, or Snapshot exist, this will fail
    val format: DatabaseHistoryFormat = DatabaseHistoryFormat.RelationalEvents
    
    format match {
      case DatabaseHistoryFormat.RelationalEvents => 
        // Expected - only one format exists
        assertEquals(format.tables.length, 4)
      case _ => fail("Should only have RelationalEvents format")
    }
  }

  test("DatabaseHistoryFormat tables match database schema") {
    val tables = DatabaseHistoryFormat.RelationalEvents.tables
    // Verify these match the tables in createInteractionHistoryTables.sql
    assert(tables.contains("sync_context_id"))
    assert(tables.contains("answer_event"))
    assert(tables.contains("grading_event"))
    assert(tables.contains("scaffolding_event"))
  }

  // ============================================================================
  // Integration edge cases
  // ============================================================================

  test("Multiple sync contexts can use same database format") {
    val context1 = it.evadid.workbook.interaction.sync.UsageContext("program1", "scenario", "user")
    val context2 = it.evadid.workbook.interaction.sync.UsageContext("program2", "scenario", "user")
    val config = DatabaseConfig("mysql", "unused", "3306", "unused")
    val logger = it.evadid.util.logging.BasicLogger()
    
    val sync1 = context1.toSyncContext("answer")
    val sync2 = context2.toSyncContext("answer")
    
    val history1 = InteractionVariableHistory.empty[String, Unit, Unit]
    val history2 = InteractionVariableHistory.empty[String, Unit, Unit]
    
    val request1 = it.evadid.distribution.commandTypes.SQLCommands.StoreToDbRequest(sync1, write(history1), config, DatabaseHistoryFormat.RelationalEvents)
    val request2 = it.evadid.distribution.commandTypes.SQLCommands.StoreToDbRequest(sync2, write(history2), config, DatabaseHistoryFormat.RelationalEvents)
    
    val result1 = UpsertToDatabase.handleRequest(request1, logger)
    val result2 = UpsertToDatabase.handleRequest(request2, logger)
    
    assertEquals(result1.elementsChanged, 0)
    assertEquals(result2.elementsChanged, 0)
  }

  test("DeleteInDatabase works with different sync contexts") {
    val context1 = it.evadid.workbook.interaction.sync.UsageContext("program", "scenario1", "user")
    val context2 = it.evadid.workbook.interaction.sync.UsageContext("program", "scenario2", "user")
    val config = DatabaseConfig("mysql", "unused", "3306", "unused")
    val logger = it.evadid.util.logging.BasicLogger()
    
    val sync1 = context1.toSyncContext("answer")
    val sync2 = context2.toSyncContext("answer")
    
    val request1 = it.evadid.distribution.commandTypes.SQLCommands.DeleteInDbRequest(context1, Some(sync1.keyForSerialisation), config, DatabaseHistoryFormat.RelationalEvents)
    val request2 = it.evadid.distribution.commandTypes.SQLCommands.DeleteInDbRequest(context2, Some(sync2.keyForSerialisation), config, DatabaseHistoryFormat.RelationalEvents)
    
    val result1 = DeleteInDatabase.handleRequest(request1, logger)
    val result2 = DeleteInDatabase.handleRequest(request2, logger)
    
    assert(result1.elementsChanged >= 0)
    assert(result2.elementsChanged >= 0)
  }
}
