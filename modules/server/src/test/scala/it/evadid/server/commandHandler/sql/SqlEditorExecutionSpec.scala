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
  import it.evadid.core.datastructures.state.State
  import it.evadid.distribution.command.ExecutionCommand
  import it.evadid.distribution.commandTypes.LLMCommands
  import it.evadid.util.logging.BasicLogger
  import java.lang.reflect.{InvocationHandler, Method, Proxy}
  import java.net.InetAddress
  import java.sql.{Connection, PreparedStatement, ResultSet, ResultSetMetaData, Statement}
  import java.time.LocalDateTime

  private def fake[T](kind: Class[T])(handle: (String, Array[AnyRef]) => AnyRef): T =
    Proxy.newProxyInstance(kind.getClassLoader, Array(kind), new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
        handle(method.getName, Option(args).getOrElse(Array.empty[AnyRef]))
    }).asInstanceOf[T]

  private def logConnection(rows: Int): (Connection, State[Vector[Map[Int, String]]]) = {
    val parameters = State(Map.empty[Int, String])
    val writes = State(Vector.empty[Map[Int, String]])
    val statement = fake(classOf[PreparedStatement]) { (method, args) => method match {
      case "setString" => parameters.update(_ + (args(0).asInstanceOf[Integer].intValue -> args(1).asInstanceOf[String])); null
      case "setTimestamp" => null
      case "executeUpdate" => writes.update(_ :+ parameters.now()); Int.box(rows)
      case "clearParameters" => parameters.set(Map.empty); null
      case "toString" => "commandLog fixture: " + writes.now().lastOption
      case other => throw new UnsupportedOperationException(other)
    }}
    val connection = fake(classOf[Connection]) { (method, _) => method match {
      case "prepareStatement" => statement
      case other => throw new UnsupportedOperationException(other)
    }}
    connection -> writes
  }

  test("feedback command logs store only allowlisted metadata without changing the request") {
    val prompt = "private learner source Grüße\nreturn a + b;"
    val system = "private feedback instructions"
    val command = ExecutionCommand(LLMCommands.feedbackLlmCommandFactory.name,
      ujson.Obj("prompt" -> prompt, "systemPrompt" -> system, "private-unknown-key" -> "private-extra-value"))
    val original = ujson.write(command.json)
    val expected = ujson.Obj("name" -> command.name, "json" -> ujson.Obj("redacted" -> true,
      "promptLength" -> prompt.length, "systemPromptLength" -> system.length))
    val (connection, writes) = logConnection(1)
    val logger = BasicLogger()
    SqlLogCommands.handleLog(LocalDateTime.of(2026, 10, 17, 10, 0), command, None,
      InetAddress.getLoopbackAddress, logger, connection)
    val stored = writes.now().head
    assertEquals(ujson.read(stored(4)), expected)
    assertEquals(stored(3), command.name)
    assertEquals(stored(5), "")
    assertEquals(ujson.write(command.json), original)
    List(prompt, system, "private-unknown-key", "private-extra-value").foreach { secret =>
      assert(!stored(4).contains(secret))
      assert(!logger.getOut().contains(secret))
      assert(!logger.getErr().contains(secret))
    }
  }

  test("failed feedback logging also redacts malformed and unexpected request fields") {
    val payloads = List(
      ujson.Str("private raw request"), ujson.Arr("private array item"), ujson.Null,
      ujson.Obj("prompt" -> ujson.Obj("private nested key" -> "private nested value"),
        "systemPrompt" -> false, "private extra key" -> "private extra value"))
    payloads.foreach { payload =>
      val command = ExecutionCommand(LLMCommands.feedbackLlmCommandFactory.name, payload)
      val original = ujson.write(payload)
      val (connection, writes) = logConnection(0)
      val logger = BasicLogger()
      SqlLogCommands.handleLog(LocalDateTime.of(2026, 10, 17, 10, 0), command, None,
        InetAddress.getLoopbackAddress, logger, connection)
      val expected = ujson.Obj("name" -> command.name, "json" -> ujson.Obj("redacted" -> true,
        "promptLength" -> ujson.Null, "systemPromptLength" -> ujson.Null))
      assertEquals(ujson.read(writes.now().head(4)), expected)
      assert(logger.getOut().contains("Failed db logging for command:"))
      assert(!logger.getOut().contains("private"))
      assert(!logger.getErr().contains("private"))
      assertEquals(ujson.write(command.json), original)
    }
  }

  test("logging retains the original payload and failure warning for other commands") {
    val command = ExecutionCommand("another-command", ujson.Obj("prompt" -> "unchanged payload 学校"))
    val (connection, writes) = logConnection(0)
    val logger = BasicLogger()
    SqlLogCommands.handleLog(LocalDateTime.of(2026, 10, 17, 10, 0), command, None,
      InetAddress.getLoopbackAddress, logger, connection)
    assertEquals(writes.now().head(4), command.toJson)
    assertEquals(SqlLogCommands.payloadForLog(command), command.toJson)
    assert(logger.getOut().contains("Failed db logging for command: " + command.name + " (" + command.toJson + ")"))
  }

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
