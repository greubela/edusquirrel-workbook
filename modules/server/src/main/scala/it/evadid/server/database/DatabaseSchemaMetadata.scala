package it.evadid.server.database

import java.sql.{Connection, DatabaseMetaData}
import scala.util.Using

/** JDBC metadata and MySQL-specific column details, read without changing the database. */
private[database] object DatabaseSchemaMetadata {
  import DatabaseSchema.*
  import SqlStatements.rows

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
    val tables = (existing intersect requiredTables).toSeq.map { name =>
      name -> readTable(connection, metadata, catalog, name)
    }.toMap
    Snapshot(existing, routines, tables)
  }

  private def readTable(connection: Connection, metadata: DatabaseMetaData, catalog: String, name: String): Table = {
    val details = columnDetails(connection, catalog, name)
    val columns = rows(metadata.getColumns(catalog, null, name.replace("_", "\\_"), "%")) { row =>
      val columnName = row.getString("COLUMN_NAME")
      val dataType = row.getString("TYPE_NAME").toUpperCase
      val baseType = dataType.stripSuffix(" UNSIGNED")
      val detail = details(columnName)
      Column(columnName, baseType, dataType, Some(row.getInt("COLUMN_SIZE")),
        nullable = row.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
        autoIncrement = row.getString("IS_AUTOINCREMENT") == "YES",
        unsigned = dataType.contains("UNSIGNED"), detail.collation,
        Option.when(timestampTypes(baseType))(detail.timestampPrecision),
        Option.when(baseType == "ENUM")(normalizeEnum(detail.sqlType)))
    }
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

  private def columnDetails(connection: Connection, catalog: String, table: String): Map[String, ColumnDetails] =
    Using.resource(connection.prepareStatement(
      "SELECT COLUMN_NAME,COLUMN_TYPE,COLLATION_NAME,DATETIME_PRECISION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) { statement =>
      statement.setString(1, catalog)
      statement.setString(2, table)
      rows(statement.executeQuery()) { row =>
        row.getString(1) -> ColumnDetails(row.getString(2), Option(row.getString(3)), row.getInt(4))
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
