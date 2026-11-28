package it.evadid.server.database.schema

import it.evadid.sql.DatabaseHistoryFormat
import munit.FunSuite

class DatabaseSqlScriptsSpec extends FunSuite {
  test("all packaged schema scripts parse and include exactly the current history layouts") {
    val tables = DatabaseSqlScripts.tables.map(table => DatabaseSqlScripts.identifier(table.getTable.getName)).toSet
    val historyTables = (DatabaseHistoryFormat.BlobFormat.tables ++ DatabaseHistoryFormat.RelationalFormat.tables).map(_.name).toSet
    assert(historyTables.subsetOf(tables))
    assertEquals(DatabaseSqlScripts.name, "setup.sql")
    assertEquals(tables, historyTables ++ Set("user", "loginTokens", "commandLog", "syncedLog", "submissionLog"))
  }
  test("the standalone script creates tables only inside the selected database") {
    val sql = scala.util.Using.resource(scala.io.Source.fromResource(DatabaseSqlScripts.name))(_.mkString)
    val executable = sql.linesIterator.filterNot(_.trim.startsWith("--")).mkString("\n")
    assert(!"(?im)^\\s*CREATE\\s+DATABASE\\b".r.findFirstIn(executable).nonEmpty)
    assert(!"(?im)^\\s*USE\\b".r.findFirstIn(executable).nonEmpty)
    // Parse the complete script, including the temporary guard, as the standalone client would execute it.
    assert(net.sf.jsqlparser.parser.CCJSqlParserUtil.parseStatements(sql).size() > DatabaseSqlScripts.statements.size)
  }

  test("every column of the authoritative SQL setup has a MySqlType, and typed history tables agree with it") {
    import it.evadid.sql.MySqlType
    import scala.jdk.CollectionConverters.*
    val definitions = DatabaseSqlScripts.tables.map(table => DatabaseSqlScripts.identifier(table.getTable.getName) -> table).toMap
    def declared(column: net.sf.jsqlparser.statement.create.table.ColumnDefinition): Option[MySqlType] =
      MySqlType.fromSql((column.getColDataType.toString +: Option(column.getColumnSpecs).toSeq.flatMap(_.asScala)).mkString(" "))
    definitions.foreach { (table, definition) =>
      definition.getColumnDefinitions.asScala.foreach(column =>
        assert(declared(column).nonEmpty, s"$table.${column.getColumnName}: ${column.getColDataType} ${column.getColumnSpecs}"))
    }
    (DatabaseHistoryFormat.BlobFormat.tables ++ DatabaseHistoryFormat.RelationalFormat.tables).foreach { table =>
      val columns = definitions(table.name).getColumnDefinitions.asScala.toSeq
      assertEquals(table.columns.map(_.columnName), columns.map(column => DatabaseSqlScripts.identifier(column.getColumnName)))
      table.columns.zip(columns).foreach((column, sql) => assertEquals(declared(sql), Some(column.mySqlType), s"${table.name}.${column.columnName}"))
    }
  }

}
