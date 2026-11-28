package it.evadid.server.database

import it.evadid.core.datastructures.state.State
import it.evadid.util.logging.BasicLogger
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.{Connection, PreparedStatement, ResultSet, SQLException, Timestamp}
import java.time.Instant
import munit.FunSuite

class SqlStatementsSpec extends FunSuite {
  private def jdbc[A](api: Class[A])(call: (String, Array[Object]) => Object): A =
    Proxy.newProxyInstance(api.getClassLoader, Array(api), new InvocationHandler {
      override def invoke(proxy: Object, method: Method, args: Array[Object]): Object =
        if method.getName == "toString" then "test JDBC resource" else call(method.getName, Option(args).getOrElse(Array.empty))
    }).asInstanceOf[A]

  /** Statements answering `execute`; every JDBC call is recorded by method name and arguments. */
  private def connection(execute: String => Object): (Connection, State[Vector[String]]) = {
    val calls = State(Vector.empty[String])
    val statement = jdbc(classOf[PreparedStatement]) { (name, args) =>
      calls.update(_ :+ (name +: args.map(String.valueOf).toList).mkString(" "))
      if name.startsWith("execute") then execute(name) else null
    }
    jdbc(classOf[Connection])((name, _) => if name == "prepareStatement" then statement else throw AssertionError(name)) -> calls
  }

  private def resultSet(values: List[String], closes: State[Int]): ResultSet = {
    val index = State(-1)
    jdbc(classOf[ResultSet]) { (name, _) => name match {
      case "next" => Boolean.box(index.update(_ + 1).now() < values.size)
      case "getString" => values(index.now())
      case "close" => closes.update(_ + 1); null
      case other => throw AssertionError(other)
    }}
  }

  test("updates bind typed values in order and close the statement") {
    val (db, calls) = connection(_ => Int.box(2))
    val instant = java.time.Instant.parse("2026-10-13T01:40:00Z")
    assertEquals(SqlStatements(db, BasicLogger()).update("UPDATE t SET a=?, b=?, c=?", "text", 7L, instant), 2)
    // Instants are bound as UTC wall times, whatever the server's zone.
    assertEquals(calls.now(), Vector("setString 1 text", "setLong 2 7", "setTimestamp 3 2026-10-13 01:40:00.0", "executeUpdate", "close"))
    assertEquals(SqlStatements.instant(SqlStatements.timestamp(instant)), instant)
  }

  test("queries read every row from the first and close the result set and statement") {
    val closes = State(0)
    val (db, calls) = connection(_ => resultSet(List("a", "b"), closes))
    assertEquals(SqlStatements(db, BasicLogger()).query("SELECT v FROM t WHERE k=?", "key")(_.getString(1)), List("a", "b"))
    assertEquals(closes.now(), 1)
    assertEquals(calls.now().last, "close")
  }

  test("failures close the statement and report diagnostics without bound values") {
    val (db, calls) = connection(_ => throw SQLException("rejected private bound value", "08001", 123))
    val logger = BasicLogger()
    val caught = intercept[SQLException](SqlStatements(db, logger).update("UPDATE t SET a=?", "private bound value"))
    assert(caught.getMessage.contains("executing SQL UPDATE t SET a=?"))
    assert(caught.getMessage.contains("SQLState=08001, errorCode=123"))
    assert(!caught.getMessage.contains("private"))
    assertEquals(caught.getCause, null)
    assertEquals(calls.now().last, "close")
  }

  test("parameters accept only strings, integer identifiers and timestamps, never null") {
    assert(scala.compiletime.testing.typeChecks("""(_: SqlStatements).update("SELECT ?, ?", "key", 123456789L)"""))
    assert(!scala.compiletime.testing.typeChecks("""(_: SqlStatements).update("SELECT ?", new Object)"""))
    val (db, calls) = connection(_ => Int.box(0))
    intercept[IllegalArgumentException](SqlStatements(db, BasicLogger()).update("SELECT ?", null.asInstanceOf[String]))
    assertEquals(calls.now(), Vector.empty)
  }
}
