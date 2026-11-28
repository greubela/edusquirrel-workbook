package it.evadid.server.sqlEditor

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
  import it.evadid.core.datastructures.state.State
  import java.lang.reflect.{InvocationHandler, Method, Proxy}
  import java.sql.{ResultSet, ResultSetMetaData, Statement}

  private def fake[T](kind: Class[T])(handle: (String, Array[AnyRef]) => AnyRef): T =
    Proxy.newProxyInstance(kind.getClassLoader, Array(kind), new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
        handle(method.getName, Option(args).getOrElse(Array.empty[AnyRef]))
    }).asInstanceOf[T]

  private def metadata(columns: Int, label: String): ResultSetMetaData = fake(classOf[ResultSetMetaData]) { (name, _) => name match {
    case "getColumnCount" => Int.box(columns)
    case "getColumnLabel" => label
    case other => throw new UnsupportedOperationException(other)
  }}

  test("results include duplicate labels, SQL NULL, update counts and closed result sets") {
    val row = State(-1)
    val closed = State(false)
    val columns = metadata(2, "value")
    val data = Vector(Vector(null, "NULL"), Vector("1", "two"))
    val resultSet = fake(classOf[ResultSet]) { (name, args) => name match {
      case "getMetaData" => columns
      case "next" => row.update(_ + 1); Boolean.box(row.now() < data.size)
      case "getString" => data(row.now())(args(0).asInstanceOf[Integer].intValue - 1)
      case "close" => closed.set(true); null
      case other => throw new UnsupportedOperationException(other)
    }}
    val moreResults = State(0)
    val statement = fake(classOf[Statement]) { (name, args) => name match {
      case "getResultSet" => resultSet
      case "getMoreResults" => moreResults.update(_ + 1); Boolean.box(false)
      case "getUpdateCount" => Int.box(if moreResults.now() == 1 then 3 else -1)
      case other => throw new UnsupportedOperationException(other)
    }}
    val results = SqlEditorExecution.collectResults(statement, true)
    assertEquals(results.head.columns, List("value", "value"))
    assertEquals(results.head.rows, List(List(None, Some("NULL")), List(Some("1"), Some("two"))))
    assertEquals(results(1).affectedRows, Some(3))
    assert(!results.head.truncated)
    assert(closed.now())
  }

  test("large query results are bounded and marked as truncated") {
    val row = State(0)
    val columns = metadata(1, "id")
    val rs = fake(classOf[ResultSet]) { (name, args) => name match {
      case "getMetaData" => columns
      case "next" => row.update(_ + 1); Boolean.box(row.now() <= 501)
      case "getString" => row.now().toString
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
}
