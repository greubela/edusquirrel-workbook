package it.evadid.server.database

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.ReferentialAction
import net.sf.jsqlparser.statement.create.table.{CreateTable, ForeignKeyIndex}
import scala.jdk.CollectionConverters.*

/** Inspection values only. Expected values are derived from the authoritative SQL script. */
private[database] object DatabaseSchema {
  case class Column(name: String, dataType: String, displayType: String, size: Option[Int],
                    nullable: Boolean, autoIncrement: Boolean, unsigned: Boolean,
                    collation: Option[String], timestampPrecision: Option[Int], enumDefinition: Option[String])
  enum ReferentialRule {
    case Restrict, Cascade, SetNull, SetDefault
  }
  case class ForeignKey(columns: Seq[String], referencedTable: String, referencedColumns: Seq[String],
                        onDelete: ReferentialRule, onUpdate: ReferentialRule)
  case class Table(name: String, columns: Seq[Column], primaryKey: Seq[String],
                   uniqueKeys: Set[Seq[String]], foreignKeys: Set[ForeignKey])
  case class Snapshot(existingTables: Set[String], routines: Set[String], tables: Map[String, Table])

  val textTypes: Set[String] = Set("VARCHAR", "CHAR", "TEXT", "LONGTEXT", "ENUM")
  val timestampTypes: Set[String] = Set("DATETIME", "TIMESTAMP")

  def normalizeEnum(sqlType: String): String = {
    val table = CCJSqlParserUtil.parse(s"CREATE TABLE enum_type_check (value $sqlType)") match {
      case table: CreateTable => table
      case _ => throw IllegalArgumentException("Expected a table definition while inspecting an ENUM column")
    }
    table.getColumnDefinitions.get(0).getColDataType.toString.replaceFirst("(?i)^enum", "ENUM")
  }

  def fromScripts(requiredTables: Set[String]): Seq[Table] = {
    import DatabaseSqlScripts.identifier
    val definitions = DatabaseSqlScripts.tables
    require(requiredTables.subsetOf(definitions.map(table => identifier(table.getTable.getName)).toSet), "Unknown required database table")
    definitions.filter(table => requiredTables.isEmpty || requiredTables(identifier(table.getTable.getName))).map(fromDefinition)
  }

  private[database] def fromDefinition(definition: CreateTable): Table = {
    import DatabaseSqlScripts.identifier
    def collation(tokens: Seq[String]): Option[String] = tokens.sliding(2)
      .find(pair => pair.size == 2 && pair.head.equalsIgnoreCase("COLLATE")).map(_.last.toLowerCase)
    val tableCollation = collation(Option(definition.getTableOptionsStrings).toSeq.flatMap(_.asScala))
    val definitions = definition.getColumnDefinitions.asScala.toSeq
    val columns = definitions.map { column =>
      val tokens = Option(column.getColumnSpecs).toSeq.flatMap(_.asScala)
      val specs = tokens.mkString(" ").toUpperCase
      val renderedType = column.getColDataType.toString.toUpperCase
      val baseType = renderedType.takeWhile(character => character.isLetterOrDigit || character == '_')
      val dataType = if baseType == "INTEGER" then "INT" else baseType
      def typeArgument: String = renderedType.substring(renderedType.indexOf('(') + 1, renderedType.indexOf(')')).trim
      Column(identifier(column.getColumnName), dataType, renderedType,
        Option.when(Set("VARCHAR", "CHAR")(dataType))(typeArgument.toInt),
        nullable = !specs.contains("NOT NULL") && !specs.contains("PRIMARY KEY"),
        autoIncrement = specs.contains("AUTO_INCREMENT"),
        unsigned = renderedType.contains("UNSIGNED") || specs.contains("UNSIGNED"),
        collation(tokens).orElse(tableCollation),
        Option.when(timestampTypes(dataType))(if renderedType.contains("(") then typeArgument.toInt else 0),
        Option.when(dataType == "ENUM")(column.getColDataType.toString.replaceFirst("(?i)^enum", "ENUM")))
    }
    val primary = definitions.filter(column => Option(column.getColumnSpecs).exists(_.asScala.mkString(" ").toUpperCase.contains("PRIMARY KEY")))
      .map(column => identifier(column.getColumnName))
    val indexes = Option(definition.getIndexes).toSeq.flatMap(_.asScala)
    val unique = indexes.filter(_.getType.toUpperCase.startsWith("UNIQUE"))
      .map(_.getColumnsNames.asScala.toSeq.map(identifier)).toSet ++ Option.when(primary.nonEmpty)(primary)
    val foreign = indexes.collect { case key: ForeignKeyIndex =>
      def rule(kind: ReferentialAction.Type): ReferentialRule =
        Option(key.getReferentialAction(kind)).map(_.getAction.getAction.toUpperCase).getOrElse("RESTRICT") match {
          case "CASCADE" => ReferentialRule.Cascade
          case "SET NULL" => ReferentialRule.SetNull
          case "SET DEFAULT" => ReferentialRule.SetDefault
          case "RESTRICT" | "NO ACTION" => ReferentialRule.Restrict
        }
      ForeignKey(key.getColumnsNames.asScala.toSeq.map(identifier), identifier(key.getTable.getName),
        key.getReferencedColumnNames.asScala.toSeq.map(identifier), rule(ReferentialAction.Type.DELETE), rule(ReferentialAction.Type.UPDATE))
    }.toSet
    Table(identifier(definition.getTable.getName), columns, primary, unique, foreign)
  }
}
