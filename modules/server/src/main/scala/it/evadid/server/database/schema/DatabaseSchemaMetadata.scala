package it.evadid.server.database.schema

import java.sql.{Connection, DatabaseMetaData}
import scala.util.Using

/** JDBC metadata and MySQL-specific column details, read without changing the database. */
private[database] object DatabaseSchemaMetadata {
  import DatabaseSchema.*
  import it.evadid.server.database.SqlStatements.rows

  private case class ColumnDetails(sqlType: String, collation: Option[String], timestampPrecision: Int)
  private case class KeyColumn(indexName: String, position: Int, column: String)
  private case class ForeignKeyColumn(keyName: String, position: Int, column: String,
                                      referencedTable: String, referencedColumn: String,
                                      onDelete: ReferentialRule, onUpdate: ReferentialRule)

  def read(connection: Connection, requiredTables: Set[String]): Snapshot = {
    val metadata = connection.getMetaData
    val catalog = connection.getCatalog
    val existing = rows(metadata.getTables(catalog, null, "%", null))(_.getString("TABLE_NAME")).toSet
    val routines = rows(metadata.getProcedures(catalog, null, "%"))(_.getString("PROCEDURE_NAME")).toSet ++
      rows(metadata.getFunctions(catalog, null, "%"))(_.getString("FUNCTION_NAME"))
    val required = existing intersect requiredTables
    // One read of every column, grouped by exact table name: no table name is used as a LIKE pattern.
    val columns = allColumns(connection, metadata, catalog).filter((table, _) => required(table))
    val tables = required.toSeq.map(name => name -> readTable(metadata, catalog, name, columns.getOrElse(name, Nil))).toMap
    Snapshot(existing, routines, tables)
  }

  private def allColumns(connection: Connection, metadata: DatabaseMetaData, catalog: String): Map[String, Seq[Column]] = {
    val details = columnDetails(connection, catalog)
    rows(metadata.getColumns(catalog, null, "%", "%")) { row =>
      val table = row.getString("TABLE_NAME")
      val columnName = row.getString("COLUMN_NAME")
      val dataType = row.getString("TYPE_NAME").toUpperCase
      val baseType = dataType.stripSuffix(" UNSIGNED")
      val detail = details(table -> columnName)
      table -> Column(columnName, baseType, dataType, Some(row.getInt("COLUMN_SIZE")),
        nullable = row.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
        autoIncrement = row.getString("IS_AUTOINCREMENT") == "YES",
        unsigned = dataType.contains("UNSIGNED"), detail.collation,
        Option.when(timestampTypes(baseType))(detail.timestampPrecision),
        Option.when(baseType == "ENUM")(normalizeEnum(detail.sqlType)))
    }.groupMap(_._1)(_._2)
  }

  private def readTable(metadata: DatabaseMetaData, catalog: String, name: String, columns: Seq[Column]): Table = {
    val primary = rows(metadata.getPrimaryKeys(catalog, null, name)) { row =>
      KeyColumn("PRIMARY", row.getInt("KEY_SEQ"), row.getString("COLUMN_NAME"))
    }.sortBy(_.position).map(_.column)
    val unique = rows(metadata.getIndexInfo(catalog, null, name, true, false)) { row =>
      KeyColumn(row.getString("INDEX_NAME"), row.getInt("ORDINAL_POSITION"), row.getString("COLUMN_NAME"))
    }.filter(_.column != null).groupBy(_.indexName).values.map(_.sortBy(_.position).map(_.column)).toSet
    val foreign = rows(metadata.getImportedKeys(catalog, null, name)) { row =>
      ForeignKeyColumn(row.getString("FK_NAME"), row.getInt("KEY_SEQ"), row.getString("FKCOLUMN_NAME"),
        row.getString("PKTABLE_NAME"), row.getString("PKCOLUMN_NAME"),
        referentialRule(row.getInt("DELETE_RULE")), referentialRule(row.getInt("UPDATE_RULE")))
    }.groupBy(_.keyName).values.map { parts =>
      val ordered = parts.sortBy(_.position)
      val first = ordered.head
      ForeignKey(ordered.map(_.column), first.referencedTable, ordered.map(_.referencedColumn), first.onDelete, first.onUpdate)
    }.toSet
    Table(name, columns, primary, unique, foreign)
  }

  /** MySQL column types, collations and timestamp precisions of the whole schema, keyed by (table, column). */
  private def columnDetails(connection: Connection, catalog: String): Map[(String, String), ColumnDetails] =
    Using.resource(connection.prepareStatement(
      "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,COLLATION_NAME,DATETIME_PRECISION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=?")) { statement =>
      statement.setString(1, catalog)
      rows(statement.executeQuery()) { row =>
        (row.getString(1), row.getString(2)) -> ColumnDetails(row.getString(3), Option(row.getString(4)), row.getInt(5))
      }.toMap
    }

  private def referentialRule(rule: Int): ReferentialRule = rule match {
    case DatabaseMetaData.importedKeyCascade => ReferentialRule.Cascade
    case DatabaseMetaData.importedKeySetNull => ReferentialRule.SetNull
    case DatabaseMetaData.importedKeySetDefault => ReferentialRule.SetDefault
    case DatabaseMetaData.importedKeyRestrict | DatabaseMetaData.importedKeyNoAction => ReferentialRule.Restrict
    case other => throw IllegalArgumentException(s"Unknown foreign key action in database metadata: $other")
  }
}
