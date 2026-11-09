package it.evadid.server.database

import it.evadid.sql.DatabaseConfig
import it.evadid.util.logging.Logger
import java.sql.{Connection, DatabaseMetaData, DriverManager}
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

/** Schema inspection reads only metadata. Setup refuses every nonempty database. */
object EnsureDatabaseProperlySetup {
  case class Report(reachable: Boolean, existingTables: Set[String], missingTables: Set[String], conflicts: List[String],
                    created: Boolean = false) {
    def valid: Boolean = reachable && missingTables.isEmpty && conflicts.isEmpty
  }

  private def rows[A](result: java.sql.ResultSet)(read: java.sql.ResultSet => A): List[A] =
    Using.resource(result)(rs => Iterator.continually(rs.next()).takeWhile(identity).map(_ => read(rs)).toList)

  def inspect(connection: Connection): Report = {
    import DatabaseSqlScripts.identifier
    val metadata = connection.getMetaData
    val catalog = connection.getCatalog
    val existing = rows(metadata.getTables(catalog, null, "%", null))(_.getString("TABLE_NAME")).toSet
    val definitions = DatabaseSqlScripts.tables
    val expected = definitions.map(table => identifier(table.getTable.getName)).toSet
    val conflicts = definitions.filter(table => existing(identifier(table.getTable.getName))).flatMap { definition =>
      val name = identifier(definition.getTable.getName)
      val columns = rows(metadata.getColumns(catalog, null, name.replace("_", "\\_"), "%")) { rs =>
        rs.getString("COLUMN_NAME") -> (rs.getString("TYPE_NAME").toUpperCase, rs.getInt("COLUMN_SIZE"),
          rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable, rs.getString("IS_AUTOINCREMENT") == "YES")
      }.toMap
      val expectedColumns = definition.getColumnDefinitions.asScala.toList
      val columnErrors = expectedColumns.flatMap { column =>
        val columnName = identifier(column.getColumnName)
        val specs = Option(column.getColumnSpecs).toList.flatMap(_.asScala).mkString(" ").toUpperCase
        val renderedType = column.getColDataType.toString.toUpperCase
        val dataType = renderedType.takeWhile(character => character.isLetterOrDigit || character == '_')
        val expectedType = if dataType == "INTEGER" then "INT" else dataType
        val size = Option.when(Set("VARCHAR", "CHAR").contains(expectedType)) {
          renderedType.substring(renderedType.indexOf('(') + 1, renderedType.indexOf(')')).trim.toInt
        }
        val unsigned = renderedType.contains("UNSIGNED") || specs.contains("UNSIGNED")
        val nullable = !specs.contains("NOT NULL") && !specs.contains("PRIMARY KEY")
        columns.get(columnName) match {
          case None => List(s"$name.$columnName is missing")
          case Some((actualType, actualSize, actualNullable, auto)) =>
            val baseType = actualType.stripSuffix(" UNSIGNED")
            List(
              Option.when(baseType != expectedType || actualType.contains("UNSIGNED") != unsigned)(s"$name.$columnName type differs: $actualType vs $renderedType"),
              Option.when(size.exists(_ != actualSize))(s"$name.$columnName length differs"),
              Option.when(nullable != actualNullable)(s"$name.$columnName nullability differs"),
              Option.when(auto != specs.contains("AUTO_INCREMENT"))(s"$name.$columnName auto-increment differs")
            ).flatten
        }
      }
      val extraColumns = (columns.keySet -- expectedColumns.map(column => identifier(column.getColumnName))).toList
        .map(column => s"$name.$column is unexpected")
      val primary = rows(metadata.getPrimaryKeys(catalog, null, name))(rs => rs.getInt("KEY_SEQ") -> rs.getString("COLUMN_NAME"))
        .sortBy(_._1).map(_._2)
      val expectedPrimary = expectedColumns.filter(column => Option(column.getColumnSpecs).exists(_.asScala.mkString(" ").toUpperCase.contains("PRIMARY KEY")))
        .map(column => identifier(column.getColumnName))
      val indexes = Option(definition.getIndexes).toList.flatMap(_.asScala)
      val unique = rows(metadata.getIndexInfo(catalog, null, name, true, false)) { rs =>
        (rs.getString("INDEX_NAME"), rs.getInt("ORDINAL_POSITION"), rs.getString("COLUMN_NAME"))
      }.filter(_._3 != null).groupBy(_._1).values.map(_.sortBy(_._2).map(_._3)).toSet
      val uniqueErrors = indexes.filter(index => index.getType.toUpperCase.startsWith("UNIQUE")).flatMap { index =>
        val key = index.getColumnsNames.asScala.toList.map(identifier)
        Option.when(!unique(key))(s"$name unique key (${key.mkString(",")}) is missing")
      }
      val foreign = rows(metadata.getImportedKeys(catalog, null, name)) { rs =>
        (rs.getString("FK_NAME"), rs.getInt("KEY_SEQ"), rs.getString("FKCOLUMN_NAME"), rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME"))
      }.groupBy(_._1).values.map { parts =>
        val ordered = parts.sortBy(_._2)
        (ordered.map(_._3), ordered.head._4, ordered.map(_._5))
      }.toSet
      val foreignErrors = indexes.collect { case key: ForeignKeyIndex => key }.flatMap { key =>
        val signature = (key.getColumnsNames.asScala.toList.map(identifier), identifier(key.getTable.getName),
          key.getReferencedColumnNames.asScala.toList.map(identifier))
        Option.when(!foreign(signature))(s"$name foreign key ${signature._1.mkString(",")} differs")
      }
      columnErrors ++ extraColumns ++ Option.when(primary != expectedPrimary)(s"$name primary key differs") ++ uniqueErrors ++ foreignErrors
    }
    Report(true, existing, expected -- existing, conflicts)
  }

  /** No ALTER/DROP/UPDATE, and no CREATE at all when any table/view already exists. */
  def setupIfEmpty(connection: Connection): Report = {
    val before = inspect(connection)
    if before.existingTables.nonEmpty || before.conflicts.nonEmpty then before
    else {
      // Parse and validate every script before executing the first DDL statement.
      val statements = DatabaseSqlScripts.statements.map(_.toString)
      Using.resource(connection.createStatement()) { statement => statements.foreach(statement.executeUpdate) }
      inspect(connection).copy(created = true)
    }
  }

  def check(config: DatabaseConfig, initializeEmpty: Boolean = false): Report =
    Try(Using.resource(DriverManager.getConnection(config.jdbcUrl + "?connectTimeout=3000&socketTimeout=3000", config.user, config.password)) { connection =>
      if initializeEmpty then setupIfEmpty(connection) else inspect(connection)
    }).fold(error => Report(false, Set.empty, Set.empty, List(Option(error.getMessage).getOrElse(error.getClass.getSimpleName))), identity)

  def onStartup(logger: Logger): Report = {
    val report = check(DatabaseConfig.readFromEnv(), initializeEmpty = true)
    if !report.valid then logger.logWarn(s"Database schema check: reachable=${report.reachable}; missing=${report.missingTables.mkString(",")}; conflicts=${report.conflicts.mkString("; ")}")
    report
  }
}
