package it.evadid.sql

import it.evadid.sql.MySqlType.*
import munit.FunSuite
import scala.compiletime.constValue
import scala.deriving.Mirror
import upickle.default.{read, write}

class SqlSchemaSpec extends FunSuite {
  /** One value of every MySqlType case, including bounds. */
  private val samples: List[MySqlType] = List(
    TinyInt(), SmallInt(unsigned = true), MediumInt(), Integer(unsigned = true), BigInt(), Decimal(65, 30), Float, Double,
    Bit(64), Boolean, Char(255), VarChar(65535), Binary(16), VarBinary(0), TinyText, Text, MediumText, LongText,
    TinyBlob, Blob, MediumBlob, LongBlob, EnumOf(List("draft", "it's", "NOT NULL")), SetOf(List("a", "b")),
    Date, Time(3), DateTime(6), Timestamp(), Year, Json)

  test("every MySQL type has a sample") {
    val mirror = summon[Mirror.SumOf[MySqlType]]
    assertEquals(samples.map(_.ordinal).toSet, (0 until constValue[Tuple.Size[mirror.MirroredElemLabels]]).toSet)
  }

  test("each type's canonical SQL reads back to the same type, and its codec round-trips") {
    samples.foreach { sample =>
      assertEquals(sample.problem, None, sample.toString)
      assertEquals(MySqlType.fromSql(sample.sql), Some(sample), sample.sql)
      assertEquals(MySqlType.fromSql(sample.sql.toLowerCase), Some(sample).map {
        case EnumOf(values) => EnumOf(values.map(_.toLowerCase))
        case SetOf(values) => SetOf(values.map(_.toLowerCase))
        case other => other
      }, "keywords are case-insensitive; quoted members keep their spelling")
    }
  }

  test("column declarations read with MySQL's synonyms, display widths and trailing attributes") {
    val declarations = Map(
      "BIGINT UNSIGNED NOT NULL AUTO_INCREMENT" -> BigInt(unsigned = true),
      "bigint(20) unsigned" -> BigInt(unsigned = true),
      "INTEGER" -> Integer(), "INT(11) SIGNED NOT NULL" -> Integer(),
      "TINYINT(1)" -> Boolean, "BOOL" -> Boolean, "TINYINT(4)" -> TinyInt(),
      "DOUBLE PRECISION" -> Double, "REAL" -> Double,
      "NUMERIC(8,2)" -> Decimal(8, 2), "DEC(5)" -> Decimal(5, 0), "DECIMAL" -> Decimal(10, 0),
      "VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL" -> VarChar(36),
      "CHAR" -> Char(1), "CHARACTER VARYING(12)" -> VarChar(12),
      "DATETIME(6) NOT NULL" -> DateTime(6), "TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP" -> Timestamp(),
      "LONGTEXT NULL" -> LongText, "JSON NOT NULL" -> Json,
      "ENUM('NOT NULL', 'b') NOT NULL DEFAULT 'b'" -> EnumOf(List("NOT NULL", "b")),
      "SET('x','y') NULL" -> SetOf(List("x", "y")))
    declarations.foreach((declaration, expected) => assertEquals(MySqlType.fromSql(declaration), Some(expected), declaration))
  }

  test("types MySQL would reject, unknown and spatial types are not read") {
    List("GEOMETRY", "POINT", "VARCHAR", "VARCHAR(70000)", "CHAR(256)", "DECIMAL(66,2)", "DECIMAL(5,6)", "DECIMAL(40,31)",
      "BIT(0)", "BIT(65)", "DATETIME(7)", "TIME(-1)", "TEXT UNSIGNED", "JSON(2)", "ENUM()", "ENUM('a','A')", "ENUM(a)",
      "SET(" + (1 to 65).map(i => s"'v$i'").mkString(",") + ")", "INT(x)", "", "VARCHAR(10").foreach { declaration =>
      assertEquals(MySqlType.fromSql(declaration), None, declaration)
    }
    intercept[IllegalArgumentException](DatabaseColumn("name", VarChar(70000)))
    intercept[IllegalArgumentException](DatabaseColumn("name", EnumOf(Nil)))
    assert(Decimal(5, 6).problem.exists(_.contains("scale")))
  }

  test("column and table identifiers are restricted to safe SQL identifiers") {
    List("1abc", "a-b", "a b", "a;DROP TABLE x", "commandLog`; DROP TABLE user; --", "", "x" * 65).foreach { name =>
      intercept[IllegalArgumentException](DatabaseColumn(name, Json))
      intercept[IllegalArgumentException](DatabaseTable(name))
    }
    assertEquals(DatabaseColumn("_ok9", Json).columnName, "_ok9")
    assertEquals(DatabaseTable("x" * 64).name.length, 64)
  }

  test("history formats keep their wire names, shared identity columns and unique tables") {
    import DatabaseHistoryFormat.*
    val formats: List[DatabaseHistoryFormat] = List(RelationalFormat, BlobFormat)
    formats.foreach(format => assertEquals(read[DatabaseHistoryFormat](write(format)), format))
    assertEquals(formats.map(format => ujson.read(write(format)).str), List("RelationalFormat", "BlobFormat"))
    val tables = formats.flatMap(_.tables)
    assertEquals(tables.map(_.name).distinct.size, tables.size)
    tables.foreach { table =>
      assertEquals(table.columns.head, DatabaseColumn("id", BigInt(unsigned = true)), table.name)
      assertEquals(table.columns.map(_.columnName).distinct.size, table.columns.size, table.name)
    }
    val identity = List("program_id", "scenario_id", "user_id", "variable_id", "format")
    assertEquals(RelationalFormat.tables.head.columns.map(_.columnName).tail, identity)
    assertEquals(BlobFormat.tables.head.columns.map(_.columnName), ("id" +: identity) :+ "history_json")
    val events = RelationalFormat.tables.tail
    assertEquals(events.map(_.name), List("answer_event", "scaffolding_event", "grading_event"))
    assertEquals(events.map(_.columns).distinct.size, 1, "every event table has the same columns")
    assertEquals(events.head.columns.find(_.columnName == "serialized_data").map(_.mySqlType), Some(Json))
  }

  test("database configurations build JDBC URLs, drop credentials and never print them") {
    val withCredentials = DatabaseConfigWithCredentials("mysql", "db.example", "3306", "school", "private-user", "private-password")
    assertEquals(withCredentials.jdbcUrl, "jdbc:mysql://db.example:3306/school")
    assertEquals(withCredentials.withoutCredentials, DatabaseConfig("mysql", "db.example", "3306", "school"))
    assert(!withCredentials.toString.contains("private"), withCredentials.toString)
    assert(!write[DatabaseConfiguration](withCredentials.withoutCredentials).contains("private"))
    assertEquals(BackendDatabase.withoutCredentials, BackendDatabase)
    List[DatabaseConfiguration](BackendDatabase, withCredentials, withCredentials.withoutCredentials).foreach(configuration =>
      assertEquals(read[DatabaseConfiguration](write(configuration)), configuration))
  }
}
