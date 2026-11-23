package it.evadid.server.commandHandler.sql

import it.evadid.distribution.commandTypes.SqlEditorCommands.*
import it.evadid.server.database.MysqlTestDatabase
import it.evadid.workbook.elements.interactionElements.sql.SqlDatabaseConfig
import munit.FunSuite

/** The SQL editor against a real MySQL teaching database: results, errors and the schema shown next to them. */
class SqlEditorExecutionMysqlSpec extends FunSuite {
  private val database = SqlDatabaseConfig(sys.env.getOrElse("SQL_DATABASE", "edusquirrel_account_test"))
  private def run(sql: String) = SqlEditorExecution.handle(Request(database, Some(sql)))

  override def beforeEach(context: BeforeEach): Unit = MysqlTestDatabase.withConnection(MysqlTestDatabase.clearSchema)
  override def afterEach(context: AfterEach): Unit = MysqlTestDatabase.withConnection(MysqlTestDatabase.clearSchema)

  test("opening the editor connects and shows the schema without running anything") {
    run("CREATE TABLE school_class (id INT PRIMARY KEY, label VARCHAR(20) NOT NULL)")
    val opened = SqlEditorExecution.handle(Request(database))
    assertEquals((opened.status, opened.message, opened.results), (Status.Ready, "Database connected.", Nil))
    assertEquals(opened.schema.map(_.name), List("school_class"))
  }

  test("statements report affected rows, queries their rows, and the schema lists keys, nullability and references") {
    assertEquals(run("CREATE TABLE school_class (id INT PRIMARY KEY, label VARCHAR(20) NOT NULL)").status, Status.Ready)
    run("CREATE TABLE pupil (id INT PRIMARY KEY, name VARCHAR(40) NOT NULL, nickname VARCHAR(40), class_id INT, " +
      "FOREIGN KEY (class_id) REFERENCES school_class(id))")
    assertEquals(run("INSERT INTO school_class VALUES (1, '5a'), (2, '5b')").results, List(Result(affectedRows = Some(2))))
    run("INSERT INTO pupil VALUES (1, 'Ada', NULL, 1), (2, 'Alan', 'Al', 2)")
    val query = run("SELECT p.name, p.nickname, c.label FROM pupil p JOIN school_class c ON c.id = p.class_id ORDER BY p.id")
    assertEquals((query.status, query.message), (Status.Ready, "SQL command completed."))
    assertEquals(query.results, List(Result(List("name", "nickname", "label"), List(List(Some("Ada"), None, Some("5a")),
      List(Some("Alan"), Some("Al"), Some("5b"))))))
    val pupil = query.schema.find(_.name == "pupil").getOrElse(fail("pupil table missing"))
    assertEquals(pupil.columns.map(column => (column.name, column.nullable, column.primaryKey)),
      List(("id", false, true), ("name", false, false), ("nickname", true, false), ("class_id", true, false)))
    assertEquals(pupil.foreignKeys, List(ForeignKey("class_id", "school_class", "id")))
    assertEquals(query.schema.map(_.name), List("pupil", "school_class"))
    assertEquals(query.schemaError, None)
  }

  test("a table name with SQL wildcards reads only its own columns") {
    run("CREATE TABLE a_b (only_here INT)")
    run("CREATE TABLE axb (other INT)")
    val schema = run("SELECT 1").schema
    assertEquals(schema.find(_.name == "a_b").map(_.columns.map(_.name)), Some(List("only_here")))
  }

  test("results beyond the row limit are cut off and marked as truncated") {
    run("CREATE TABLE numbers (n INT PRIMARY KEY)")
    run("INSERT INTO numbers WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 600) SELECT n FROM seq")
    val result = run("SELECT n FROM numbers ORDER BY n").results.head
    assertEquals((result.rows.size, result.truncated, result.rows.last), (500, true, List(Some("500"))))
  }

  test("SQL errors keep the connection usable and report MySQL's state and code") {
    val error = run("SELEC nothing")
    assertEquals(error.status, Status.QueryError)
    assert(error.message.startsWith("SQL error (42000, 1064)"), error.message)
    assertEquals(run("SELECT 1").status, Status.Ready)
    assertEquals(run("   ").message, "Enter a SQL command (at most 100000 characters).")
    assertEquals(run("SELECT 1 FROM missing_table").status, Status.QueryError)
  }

  test("only allow-listed teaching databases are opened") {
    val refused = SqlEditorExecution.handle(Request(SqlDatabaseConfig("mysql"), Some("SELECT 1")))
    assertEquals((refused.status, refused.results), (Status.ConfigurationError, Nil))
    assertEquals(SqlEditorExecution.handle(Request(SqlDatabaseConfig("edusquirrel_account_test; DROP"), None)).status, Status.ConfigurationError)
  }
}
