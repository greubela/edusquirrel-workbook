package it.evadid.sql

import upickle.default.*

/** MySQL 8 column types with their canonical DDL spelling ([[sql]]) and limits ([[problem]]).
  * [[MySqlType.fromSql]] reads a column declaration's type, including MySQL's synonyms.
  * Spatial types are not supported.
  */
enum MySqlType derives ReadWriter {
  case TinyInt(unsigned: Boolean = false)
  case SmallInt(unsigned: Boolean = false)
  case MediumInt(unsigned: Boolean = false)
  case Integer(unsigned: Boolean = false)
  case BigInt(unsigned: Boolean = false)
  case Decimal(precision: Int = 10, scale: Int = 0)
  case Float
  case Double
  case Bit(length: Int = 1)
  /** `BOOL`/`BOOLEAN`, MySQL's synonym for `TINYINT(1)`. */
  case Boolean
  case Char(length: Int = 1)
  case VarChar(length: Int)
  case Binary(length: Int = 1)
  case VarBinary(length: Int)
  case TinyText
  case Text
  case MediumText
  case LongText
  case TinyBlob
  case Blob
  case MediumBlob
  case LongBlob
  case EnumOf(values: Seq[String])
  case SetOf(values: Seq[String])
  case Date
  case Time(precision: Int = 0)
  case DateTime(precision: Int = 0)
  case Timestamp(precision: Int = 0)
  case Year
  case Json

  /** The canonical DDL spelling, as [[MySqlType.fromSql]] reads it back. */
  def sql: String = this match {
    case TinyInt(unsigned) => integer("TINYINT", unsigned)
    case SmallInt(unsigned) => integer("SMALLINT", unsigned)
    case MediumInt(unsigned) => integer("MEDIUMINT", unsigned)
    case Integer(unsigned) => integer("INT", unsigned)
    case BigInt(unsigned) => integer("BIGINT", unsigned)
    case Decimal(precision, scale) => s"DECIMAL($precision,$scale)"
    case Float => "FLOAT"
    case Double => "DOUBLE"
    case Bit(length) => s"BIT($length)"
    case Boolean => "BOOLEAN"
    case Char(length) => s"CHAR($length)"
    case VarChar(length) => s"VARCHAR($length)"
    case Binary(length) => s"BINARY($length)"
    case VarBinary(length) => s"VARBINARY($length)"
    case TinyText => "TINYTEXT"
    case Text => "TEXT"
    case MediumText => "MEDIUMTEXT"
    case LongText => "LONGTEXT"
    case TinyBlob => "TINYBLOB"
    case Blob => "BLOB"
    case MediumBlob => "MEDIUMBLOB"
    case LongBlob => "LONGBLOB"
    case EnumOf(values) => values.map(MySqlType.quote).mkString("ENUM(", ",", ")")
    case SetOf(values) => values.map(MySqlType.quote).mkString("SET(", ",", ")")
    case Date => "DATE"
    case Time(precision) => fractional("TIME", precision)
    case DateTime(precision) => fractional("DATETIME", precision)
    case Timestamp(precision) => fractional("TIMESTAMP", precision)
    case Year => "YEAR"
    case Json => "JSON"
  }

  /** Why MySQL would reject this type, if it would. */
  def problem: Option[String] = {
    def within(label: String, value: Int, low: Int, high: Int) =
      Option.unless(value >= low && value <= high)(s"$label must be between $low and $high, not $value")
    this match {
      case Decimal(precision, scale) => within("DECIMAL precision", precision, 1, 65)
        .orElse(within("DECIMAL scale", scale, 0, math.min(30, precision)))
      case Bit(length) => within("BIT length", length, 1, 64)
      case Char(length) => within("CHAR length", length, 0, 255)
      case VarChar(length) => within("VARCHAR length", length, 0, 65535)
      case Binary(length) => within("BINARY length", length, 0, 255)
      case VarBinary(length) => within("VARBINARY length", length, 0, 65535)
      case EnumOf(values) => members("ENUM", values, 65535)
      case SetOf(values) => members("SET", values, 64)
      case Time(precision) => within("TIME precision", precision, 0, 6)
      case DateTime(precision) => within("DATETIME precision", precision, 0, 6)
      case Timestamp(precision) => within("TIMESTAMP precision", precision, 0, 6)
      case _ => None
    }
  }

  private def members(label: String, values: Seq[String], maximum: Int): Option[String] =
    if values.isEmpty then Some(s"$label needs at least one member")
    else if values.size > maximum then Some(s"$label allows at most $maximum members")
    else Option.when(values.map(_.toLowerCase).distinct.size != values.size)(s"$label members must be distinct ignoring case")

  private def integer(name: String, unsigned: scala.Boolean) = if unsigned then s"$name UNSIGNED" else name
  private def fractional(name: String, precision: Int) = if precision == 0 then name else s"$name($precision)"
}

object MySqlType {
  private def quote(value: String): String = "'" + value.replace("'", "''") + "'"

  private val integerTypes: Map[String, scala.Boolean => MySqlType] = Map(
    "TINYINT" -> (TinyInt(_)), "SMALLINT" -> (SmallInt(_)), "MEDIUMINT" -> (MediumInt(_)),
    "INT" -> (Integer(_)), "INTEGER" -> (Integer(_)), "BIGINT" -> (BigInt(_)))
  private val plainTypes: Map[String, MySqlType] = Map(
    "FLOAT" -> Float, "DOUBLE" -> Double, "DOUBLE PRECISION" -> Double, "REAL" -> Double,
    "BOOL" -> Boolean, "BOOLEAN" -> Boolean,
    "TINYTEXT" -> TinyText, "TEXT" -> Text, "MEDIUMTEXT" -> MediumText, "LONGTEXT" -> LongText,
    "TINYBLOB" -> TinyBlob, "BLOB" -> Blob, "MEDIUMBLOB" -> MediumBlob, "LONGBLOB" -> LongBlob,
    "DATE" -> Date, "YEAR" -> Year, "JSON" -> Json)
  /** Column attributes that may follow the type, which are not part of it. */
  private val attributes = "(?i)\\s+(CHARACTER\\s+SET|CHARSET|COLLATE|NOT\\s+NULL|NULL|DEFAULT|AUTO_INCREMENT|PRIMARY|UNIQUE|COMMENT|ZEROFILL|GENERATED|CHECK|REFERENCES)\\b.*$".r
  private val withArguments = "(?is)([A-Z ]+?)\\s*\\((.*)\\)\\s*(UNSIGNED|SIGNED)?".r
  private val withoutArguments = "(?i)([A-Z ]+?)(?:\\s+(UNSIGNED|SIGNED))?".r

  /** The type of a column declaration such as `BIGINT UNSIGNED NOT NULL` or `VARCHAR(36) CHARACTER SET ascii`.
    * Returns `None` for unknown, spatial or invalid types.
    */
  def fromSql(declaration: String): Option[MySqlType] = {
    val normalized = declaration.trim.replaceAll("\\s+", " ")
    // Attributes follow the argument list, so quoted ENUM or SET members are never mistaken for them.
    val close = normalized.lastIndexOf(')')
    val text = (normalized.take(close + 1) + attributes.replaceFirstIn(normalized.drop(close + 1), "")).trim
    val parsed = text match {
      case withArguments(name, arguments, sign) => withArgumentsType(name.trim.toUpperCase, arguments, sign != null && sign.equalsIgnoreCase("UNSIGNED"))
      case withoutArguments(name, sign) => withoutArgumentsType(name.trim.toUpperCase, sign != null && sign.equalsIgnoreCase("UNSIGNED"))
      case _ => None
    }
    parsed.filter(_.problem.isEmpty)
  }

  private def withoutArgumentsType(name: String, unsigned: scala.Boolean): Option[MySqlType] =
    integerTypes.get(name).map(_(unsigned)).orElse(Option.unless(unsigned)(name).flatMap(name => plainTypes.get(name).orElse(name match {
      case "DECIMAL" | "DEC" | "NUMERIC" | "FIXED" => Some(Decimal())
      case "BIT" => Some(Bit())
      case "CHAR" | "CHARACTER" => Some(Char())
      case "BINARY" => Some(Binary())
      case "TIME" => Some(Time())
      case "DATETIME" => Some(DateTime())
      case "TIMESTAMP" => Some(Timestamp())
      case _ => None
    })))

  private def withArgumentsType(name: String, arguments: String, unsigned: scala.Boolean): Option[MySqlType] = {
    lazy val numbers = arguments.split(",", -1).toList.map(_.trim.toIntOption)
    def one(make: Int => MySqlType) = numbers match {
      case List(Some(value)) => Some(make(value))
      case _ => None
    }
    if integerTypes.contains(name) then numbers match {
      // A display width is ignored by MySQL 8, except TINYINT(1), which is how BOOLEAN reads back.
      case List(Some(1)) if name == "TINYINT" && !unsigned => Some(Boolean)
      case List(Some(_)) => Some(integerTypes(name)(unsigned))
      case _ => None
    }
    else if unsigned then None
    else name match {
      case "DECIMAL" | "DEC" | "NUMERIC" | "FIXED" => numbers match {
        case List(Some(precision)) => Some(Decimal(precision, 0))
        case List(Some(precision), Some(scale)) => Some(Decimal(precision, scale))
        case _ => None
      }
      case "BIT" => one(Bit(_))
      case "CHAR" | "CHARACTER" => one(Char(_))
      case "VARCHAR" | "CHARACTER VARYING" => one(VarChar(_))
      case "BINARY" => one(Binary(_))
      case "VARBINARY" => one(VarBinary(_))
      case "TIME" => one(Time(_))
      case "DATETIME" => one(DateTime(_))
      case "TIMESTAMP" => one(Timestamp(_))
      case "ENUM" => quotedList(arguments).map(EnumOf(_))
      case "SET" => quotedList(arguments).map(SetOf(_))
      case _ => None
    }
  }

  /** `'a','it''s'` with SQL quote doubling. */
  private def quotedList(arguments: String): Option[Seq[String]] = {
    val value = "'((?:[^']|'')*)'".r
    val separated = s"\\s*${value.regex}\\s*(?:,|$$)".r
    val matches = separated.findAllMatchIn(arguments).toList
    Option.when(matches.nonEmpty && matches.map(_.matched).mkString == arguments)(matches.map(_.group(1).replace("''", "'")))
  }
}

case class DatabaseColumn(columnName: String, mySqlType: MySqlType) derives ReadWriter {
  require(columnName.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"), "Invalid database column identifier")
  mySqlType.problem.foreach(problem => throw IllegalArgumentException(s"Invalid type of column $columnName: $problem"))
}
