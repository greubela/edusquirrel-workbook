package it.evadid.server.commandHandler.sql

class SqlEditorExecutionSpec extends munit.FunSuite {
  test("SQL execution requires an exact allow-listed database name") {
    assert(SqlEditorExecution.allowedDatabase("school", Set("school")))
    assert(!SqlEditorExecution.allowedDatabase("accounts", Set("school")))
    assert(!SqlEditorExecution.allowedDatabase("school", Set.empty))
    assert(!SqlEditorExecution.allowedDatabase("school?allowMultiQueries=true", Set("school?allowMultiQueries=true")))
    assert(!SqlEditorExecution.allowedDatabase("../school", Set("../school")))
  }
}

class SqlEditorResultSpec extends munit.FunSuite {
  import java.lang.reflect.{InvocationHandler, Method, Proxy}
  import java.sql.{ResultSet, ResultSetMetaData, Statement}

  private def fake[T](kind: Class[T])(handle: (String, Array[AnyRef]) => AnyRef): T =
    Proxy.newProxyInstance(kind.getClassLoader, Array(kind), new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
        handle(method.getName, Option(args).getOrElse(Array.empty[AnyRef]))
    }).asInstanceOf[T]

  test("results include duplicate labels, SQL NULL, update counts and closed result sets") {
    var row = -1
    var closed = false
    val metadata = fake(classOf[ResultSetMetaData]) { (name, args) => name match {
      case "getColumnCount" => Int.box(2)
      case "getColumnLabel" => "value"
      case other => throw new UnsupportedOperationException(other)
    }}
    val data = Vector(Vector(null, "NULL"), Vector("1", "two"))
    val resultSet = fake(classOf[ResultSet]) { (name, args) => name match {
      case "getMetaData" => metadata
      case "next" => row += 1; Boolean.box(row < data.size)
      case "getString" => data(row)(args(0).asInstanceOf[Integer].intValue - 1)
      case "close" => closed = true; null
      case other => throw new UnsupportedOperationException(other)
    }}
    var moreResults = 0
    val statement = fake(classOf[Statement]) { (name, args) => name match {
      case "getResultSet" => resultSet
      case "getMoreResults" => moreResults += 1; Boolean.box(false)
      case "getUpdateCount" => Int.box(if moreResults == 1 then 3 else -1)
      case other => throw new UnsupportedOperationException(other)
    }}
    val results = SqlEditorExecution.collectResults(statement, true)
    assertEquals(results.head.columns, List("value", "value"))
    assertEquals(results.head.rows, List(List(None, Some("NULL")), List(Some("1"), Some("two"))))
    assertEquals(results(1).affectedRows, Some(3))
    assert(!results.head.truncated)
    assert(closed)
  }

  test("large query results are bounded and marked as truncated") {
    var row = 0
    val metadata = fake(classOf[ResultSetMetaData]) { (name, args) => name match {
      case "getColumnCount" => Int.box(1)
      case "getColumnLabel" => "id"
      case other => throw new UnsupportedOperationException(other)
    }}
    val rs = fake(classOf[ResultSet]) { (name, args) => name match {
      case "getMetaData" => metadata
      case "next" => row += 1; Boolean.box(row <= 501)
      case "getString" => row.toString
      case "close" => null
      case other => throw new UnsupportedOperationException(other)
    }}
    val statement = fake(classOf[Statement]) { (name, args) => name match {
      case "getResultSet" => rs
      case "getMoreResults" => Boolean.box(false)
      case "getUpdateCount" => Int.box(-1)
      case other => throw new UnsupportedOperationException(other)
    }}
    val results = SqlEditorExecution.collectResults(statement, true)
    assertEquals(results.head.rows.size, 500)
    assert(results.head.truncated)
  }

  test("metadata table patterns escape SQL wildcard characters") {
    assertEquals(SqlEditorExecution.escapePattern("student_records", "\\"), "student\\_records")
    assertEquals(SqlEditorExecution.escapePattern("test%table", "\\"), "test\\%table")
  }
}
