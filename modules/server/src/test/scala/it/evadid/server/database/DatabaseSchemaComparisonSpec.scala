package it.evadid.server.database

import munit.FunSuite
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.create.table.CreateTable

class DatabaseSchemaComparisonSpec extends FunSuite {
  import DatabaseSchema.*

  private def schema(sql: String): Table = fromDefinition(CCJSqlParserUtil.parse(sql).asInstanceOf[CreateTable])
  private def differences(expected: Table, actual: Table): Set[String] = DatabaseSchemaComparison.compare(expected, actual).toSet
  private def scripted(name: String): Table = fromScripts(Set(name)).head

  test("script selection accepts only defined table names") {
    val selected = fromScripts(Set("answer_event", "sync_context_id"))
    assertEquals(selected.map(_.name).toSet, Set("answer_event", "sync_context_id"))
    assert(fromScripts(Set.empty).size > selected.size)
    intercept[IllegalArgumentException](fromScripts(Set("not_an_application_table")))
  }

  test("column comparison detects each storage-contract difference") {
    val expected = scripted("sync_context_id")
    val actual = expected.copy(columns = expected.columns.map {
      case column if column.name == "id" => column.copy(dataType = "INT", displayType = "INT", unsigned = false, autoIncrement = false)
      case column if column.name == "program_id" => column.copy(size = Some(12), nullable = true, collation = Some("utf8mb4_general_ci"))
      case column => column
    })
    assertEquals(differences(expected, actual), Set(
      "sync_context_id.id type differs: INT vs BIGINT",
      "sync_context_id.id auto-increment differs",
      "sync_context_id.program_id length differs",
      "sync_context_id.program_id nullability differs",
      "sync_context_id.program_id collation differs"))
  }

  test("column and index iteration order does not affect comparison; key column order does") {
    val expected = scripted("answer_event")
    assertEquals(differences(expected, expected.copy(columns = expected.columns.reverse)), Set.empty[String])
    val reversed = expected.copy(uniqueKeys = expected.uniqueKeys.map(_.reverse))
    assertEquals(differences(expected, reversed), Set(
      "answer_event unique key (sync_id,event_id) is missing",
      "answer_event has unexpected uniqueness (event_id,sync_id)"))
  }

  test("missing and extra columns, primary keys and additional uniqueness are diagnosed") {
    val expected = scripted("answer_event")
    val actual = expected.copy(columns = expected.columns.filterNot(_.name == "serialized_data") :+
      expected.columns.head.copy(name = "unexpected"), primaryKey = Seq("event_id"),
      uniqueKeys = expected.uniqueKeys + Seq("sync_id"))
    assertEquals(differences(expected, actual), Set(
      "answer_event.serialized_data is missing",
      "answer_event.unexpected is unexpected",
      "answer_event primary key differs",
      "answer_event has unexpected uniqueness (sync_id)"))
  }

  test("foreign keys compare referenced columns and actions, independently of constraint names") {
    val expected = scripted("answer_event")
    val actual = expected.copy(foreignKeys = expected.foreignKeys.map(_.copy(onDelete = ReferentialRule.Restrict)))
    assertEquals(differences(expected, actual), Set(
      "answer_event foreign key sync_id differs", "answer_event has unexpected foreign key sync_id"))
    val restrict = schema("CREATE TABLE child (id INT PRIMARY KEY, parent_id INT, CONSTRAINT first_name FOREIGN KEY (parent_id) REFERENCES parent(id))")
    val noAction = schema("CREATE TABLE child (id INTEGER PRIMARY KEY, parent_id INTEGER, CONSTRAINT other_name FOREIGN KEY (parent_id) REFERENCES parent(id) ON DELETE NO ACTION ON UPDATE RESTRICT)")
    assertEquals(differences(restrict, noAction), Set.empty[String])
  }

  test("composite foreign keys preserve local and referenced column order") {
    val expected = schema("CREATE TABLE child (id INT PRIMARY KEY, a INT, b INT, FOREIGN KEY (a,b) REFERENCES parent(x,y) ON UPDATE CASCADE)")
    val actual = expected.copy(foreignKeys = expected.foreignKeys.map(key => key.copy(referencedColumns = key.referencedColumns.reverse)))
    assertEquals(differences(expected, actual), Set(
      "child foreign key a,b differs", "child has unexpected foreign key a,b"))
  }

  test("timestamp precision, enum value order and enum literal case are significant") {
    val expected = schema("CREATE TABLE sample (id INT PRIMARY KEY, time DATETIME(6), status ENUM('Ready','Done'))")
    val actual = schema("CREATE TABLE sample (id INT PRIMARY KEY, time DATETIME(3), status ENUM('ready','Done'))")
    assertEquals(differences(expected, actual), Set("sample.time timestamp precision differs", "sample.status enum values differ"))
    assertEquals(normalizeEnum("enum('Ready','Done')"), expected.columns.find(_.name == "status").get.enumDefinition.get)
    val reordered = schema("CREATE TABLE sample (id INT PRIMARY KEY, time DATETIME(6), status ENUM('Done','Ready'))")
    assertEquals(differences(expected, reordered), Set("sample.status enum values differ"))
  }

  test("column collation overrides the table default; unspecified collation is not imposed") {
    val expected = schema("CREATE TABLE sample (id INT PRIMARY KEY, label VARCHAR(12) COLLATE ascii_bin) COLLATE utf8mb4_bin")
    assertEquals(expected.columns.find(_.name == "label").get.collation, Some("ascii_bin"))
    val unspecified = schema("CREATE TABLE sample (id INT PRIMARY KEY, label VARCHAR(12))")
    assertEquals(differences(unspecified, expected), Set.empty[String])
  }
}
