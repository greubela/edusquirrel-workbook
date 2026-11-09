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
                    created: Boolean = false, existingRoutines: Set[String] = Set.empty) {
    def valid: Boolean = reachable && missingTables.isEmpty && conflicts.isEmpty
  }

  private def rows[A](result: java.sql.ResultSet)(read: java.sql.ResultSet => A): List[A] =
    Using.resource(result)(rs => Iterator.continually(rs.next()).takeWhile(identity).map(_ => read(rs)).toList)

  def inspect(connection: Connection): Report = {
    import DatabaseSqlScripts.identifier
    val metadata = connection.getMetaData
    val catalog = connection.getCatalog
    val existing = rows(metadata.getTables(catalog, null, "%", null))(_.getString("TABLE_NAME")).toSet
    val routines = rows(metadata.getProcedures(catalog, null, "%"))(_.getString("PROCEDURE_NAME")).toSet ++
      rows(metadata.getFunctions(catalog, null, "%"))(_.getString("FUNCTION_NAME"))
    val definitions = DatabaseSqlScripts.tables
    val expected = definitions.map(table => identifier(table.getTable.getName)).toSet
    val conflicts = definitions.filter(table => existing(identifier(table.getTable.getName))).flatMap { definition =>
      val name = identifier(definition.getTable.getName)
      def collation(tokens: Seq[String]): Option[String] = tokens.sliding(2)
        .find(pair => pair.size == 2 && pair.head.equalsIgnoreCase("COLLATE")).map(_.last.toLowerCase)
      val tableCollation = collation(Option(definition.getTableOptionsStrings).toList.flatMap(_.asScala))
      val details = Using.resource(connection.prepareStatement(
        "SELECT COLUMN_NAME,COLUMN_TYPE,COLLATION_NAME,DATETIME_PRECISION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) { statement =>
        statement.setString(1, catalog)
        statement.setString(2, name)
        rows(statement.executeQuery())(row => row.getString(1) -> (row.getString(2), Option(row.getString(3)), row.getInt(4))).toMap
      }
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
        val columnCollation = collation(Option(column.getColumnSpecs).toList.flatMap(_.asScala)).orElse(tableCollation)
        val precision = if renderedType.contains("(") then renderedType.substring(renderedType.indexOf('(') + 1, renderedType.indexOf(')')).trim else "0"
        val nullable = !specs.contains("NOT NULL") && !specs.contains("PRIMARY KEY")
        columns.get(columnName) match {
          case None => List(s"$name.$columnName is missing")
          case Some((actualType, actualSize, actualNullable, auto)) =>
            val baseType = actualType.stripSuffix(" UNSIGNED")
            List(
              Option.when(baseType != expectedType || actualType.contains("UNSIGNED") != unsigned)(s"$name.$columnName type differs: $actualType vs $renderedType"),
              Option.when(size.exists(_ != actualSize))(s"$name.$columnName length differs"),
              Option.when(nullable != actualNullable)(s"$name.$columnName nullability differs"),
              Option.when(auto != specs.contains("AUTO_INCREMENT"))(s"$name.$columnName auto-increment differs"),
              Option.when(Set("VARCHAR", "CHAR", "TEXT", "LONGTEXT", "ENUM").contains(expectedType) &&
                columnCollation.exists(expected => !details(columnName)._2.contains(expected)))(s"$name.$columnName collation differs"),
              Option.when(Set("DATETIME", "TIMESTAMP").contains(expectedType) && details(columnName)._3 != precision.toInt)(s"$name.$columnName timestamp precision differs"),
              Option.when(expectedType == "ENUM" && {
                val parsed = net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(s"CREATE TABLE enum_type_check (value ${details(columnName)._1})")
                  .asInstanceOf[net.sf.jsqlparser.statement.create.table.CreateTable].getColumnDefinitions.get(0).getColDataType.toString
                parsed.replaceFirst("(?i)^enum", "ENUM") != column.getColDataType.toString.replaceFirst("(?i)^enum", "ENUM")
              })(s"$name.$columnName enum values differ")
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
      val expectedUnique = indexes.filter(index => index.getType.toUpperCase.startsWith("UNIQUE"))
        .map(_.getColumnsNames.asScala.toList.map(identifier)).toSet ++ Option.when(expectedPrimary.nonEmpty)(expectedPrimary)
      val unexpectedUnique = (unique -- expectedUnique).toList.map(columns => s"$name has unexpected uniqueness (${columns.mkString(",")})")
      val foreign = rows(metadata.getImportedKeys(catalog, null, name)) { rs =>
        (rs.getString("FK_NAME"), rs.getInt("KEY_SEQ"), rs.getString("FKCOLUMN_NAME"), rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME"), rs.getInt("DELETE_RULE"), rs.getInt("UPDATE_RULE"))
      }.groupBy(_._1).values.map { parts =>
        val ordered = parts.sortBy(_._2)
        def normalize(rule: Int): Int = if rule == DatabaseMetaData.importedKeyNoAction then DatabaseMetaData.importedKeyRestrict else rule
        (ordered.map(_._3), ordered.head._4, ordered.map(_._5), normalize(ordered.head._6), normalize(ordered.head._7))
      }.toSet
      val expectedForeign = indexes.collect { case key: ForeignKeyIndex => key }.map { key =>
        def rule(value: String): Int = Option(value).map(_.toUpperCase).getOrElse("RESTRICT") match {
          case "CASCADE" => DatabaseMetaData.importedKeyCascade
          case "SET NULL" => DatabaseMetaData.importedKeySetNull
          case "SET DEFAULT" => DatabaseMetaData.importedKeySetDefault
          case "RESTRICT" | "NO ACTION" => DatabaseMetaData.importedKeyRestrict
        }
        import net.sf.jsqlparser.statement.ReferentialAction.Type
        def action(kind: Type): String = Option(key.getReferentialAction(kind)).map(_.getAction.getAction).orNull
        (key.getColumnsNames.asScala.toList.map(identifier), identifier(key.getTable.getName),
          key.getReferencedColumnNames.asScala.toList.map(identifier), rule(action(Type.DELETE)), rule(action(Type.UPDATE)))
      }.toSet
      val foreignErrors = (expectedForeign -- foreign).toList.map(key => s"$name foreign key ${key._1.mkString(",")} differs") ++
        (foreign -- expectedForeign).toList.map(key => s"$name has unexpected foreign key ${key._1.mkString(",")}")
      columnErrors ++ extraColumns ++ Option.when(primary != expectedPrimary)(s"$name primary key differs") ++ uniqueErrors ++ unexpectedUnique ++ foreignErrors
    }
    Report(true, existing, expected -- existing, conflicts, existingRoutines = routines)
  }

  /** No ALTER/DROP/UPDATE, and no CREATE at all when any table/view already exists. */
  def setupIfEmpty(connection: Connection): Report = {
    val before = inspect(connection)
    if before.existingTables.nonEmpty || before.existingRoutines.nonEmpty || before.conflicts.nonEmpty then before
    else {
      // Parse and validate every script before executing the first DDL statement.
      val statements = DatabaseSqlScripts.statements.map(_.toString)
      Using.resource(connection.createStatement()) { statement => statements.foreach(statement.executeUpdate) }
      inspect(connection).copy(created = true)
    }
  }

  def check(config: DatabaseConfig, initializeEmpty: Boolean = false): Report =
    Try(DriverManager.getConnection(config.jdbcUrl + "?connectTimeout=3000&socketTimeout=3000", config.user, config.password)).fold(
      error => Report(false, Set.empty, Set.empty, List(Option(error.getMessage).getOrElse(error.getClass.getSimpleName))),
      connection => Using.resource(connection) { connection =>
        Try(if initializeEmpty then setupIfEmpty(connection) else inspect(connection)).fold(
          error => Report(true, Set.empty, Set.empty, List(Option(error.getMessage).getOrElse(error.getClass.getSimpleName))), identity)
      })

  def onStartup(logger: Logger): Report = {
    val report = check(DatabaseConfig.readFromEnv(), initializeEmpty = true)
    if !report.valid then logger.logWarn(s"Database schema check: reachable=${report.reachable}; missing=${report.missingTables.mkString(",")}; conflicts=${report.conflicts.mkString("; ")}")
    report
  }
}
