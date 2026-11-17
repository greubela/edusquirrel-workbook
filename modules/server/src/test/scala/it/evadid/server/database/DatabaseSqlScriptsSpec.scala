package it.evadid.server.database

import it.evadid.sql.DatabaseHistoryFormat
import munit.FunSuite

class DatabaseSqlScriptsSpec extends FunSuite {
  test("all packaged schema scripts parse and include exactly the current history layouts") {
    val tables = DatabaseSqlScripts.tables.map(table => DatabaseSqlScripts.identifier(table.getTable.getName)).toSet
    val historyTables = (DatabaseHistoryFormat.BlobFormat.tables ++ DatabaseHistoryFormat.RelationalFormat.tables).toSet
    assert(historyTables.subsetOf(tables))
    assertEquals(DatabaseSqlScripts.name, "setup.sql")
    assertEquals(tables, historyTables ++ Set("user", "loginTokens", "commandLog", "syncedLog"))
  }
}
