package it.evadid.server.database

import it.evadid.sql.DatabaseHistoryFormat
import munit.FunSuite

class DatabaseSqlScriptsSpec extends FunSuite {
  test("all packaged schema scripts parse and include exactly the current history layouts") {
    val tables = DatabaseSqlScripts.tables.map(table => DatabaseSqlScripts.identifier(table.getTable.getName)).toSet
    val historyTables = (DatabaseHistoryFormat.BlobFormat.tables ++ DatabaseHistoryFormat.RelationalFormat.tables).map(_.name).toSet
    assert(historyTables.subsetOf(tables))
    assertEquals(DatabaseSqlScripts.name, "setup.sql")
    assertEquals(tables, historyTables ++ Set("user", "loginTokens", "commandLog", "syncedLog"))
  }
  test("typed history table columns agree with the authoritative SQL setup") {
    import it.evadid.sql.MySqlType.*
    import scala.jdk.CollectionConverters.*
    val definitions = DatabaseSqlScripts.tables.map(table => DatabaseSqlScripts.identifier(table.getTable.getName) -> table).toMap
    (DatabaseHistoryFormat.BlobFormat.tables ++ DatabaseHistoryFormat.RelationalFormat.tables).foreach { table =>
      val columns = definitions(table.name).getColumnDefinitions.asScala.toSeq
      assertEquals(table.columns.map(_.columnName), columns.map(column => DatabaseSqlScripts.identifier(column.getColumnName)))
      table.columns.zip(columns).foreach { (column, sql) =>
        val expected = column.mySqlType match {
          case BigInt(unsigned) => if unsigned then "BIGINT UNSIGNED" else "BIGINT"
          case VarChar(length) => s"VARCHAR ($length)"
          case Json => "JSON"
          case other => fail(s"Unexpected history column type: $other")
        }
        val specs = Option(sql.getColumnSpecs).toSeq.flatMap(_.asScala).map(_.toUpperCase)
        val actual = sql.getColDataType.toString.toUpperCase +
          (if specs.contains("UNSIGNED") && !sql.getColDataType.toString.toUpperCase.contains("UNSIGNED") then " UNSIGNED" else "")
        assertEquals(actual, expected, s"${table.name}.${column.columnName}")
      }
    }
  }

}
