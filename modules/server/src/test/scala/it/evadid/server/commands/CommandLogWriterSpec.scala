package it.evadid.server.commands

import it.evadid.core.datastructures.state.State
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.util.logging.BasicLogger
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.net.InetAddress
import java.sql.{Connection, PreparedStatement}
import java.time.Instant

class CommandLogWriterSpec extends munit.FunSuite {
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
      case "executeUpdate" => writes.update(_ :+ parameters.now()); Int.box(rows)
      case "clearParameters" => parameters.set(Map.empty); null
      case "close" => null
      case "toString" => "commandLog fixture: " + writes.now().lastOption
      case other => throw new UnsupportedOperationException(other)
    }}
    val connection = fake(classOf[Connection]) { (method, _) => method match {
      case "prepareStatement" => statement
      case other => throw new UnsupportedOperationException(other)
    }}
    connection -> writes
  }

  /** Logs `command` through a connection whose update reports `rows` changed rows; returns the writes and the logger. */
  private def logged(command: ExecutionCommand, rows: Int): (Vector[Map[Int, String]], BasicLogger) = {
    val (connection, writes) = logConnection(rows)
    val logger = BasicLogger()
    val log = command.storeLogIntoTable.getOrElse(fail("the command has no log"))
    CommandLogWriter.insert(log, command,
      CommandAccess.RequestContext(Instant.parse("2026-10-17T10:00:00Z"), InetAddress.getLoopbackAddress, None), logger, connection)
    writes.now() -> logger
  }

  test("feedback command logs store only allowlisted metadata without changing the request") {
    val prompt = "private learner source Grüße\nreturn a + b;"
    val system = "private feedback instructions"
    val command = ExecutionCommand(LLMCommands.feedbackLlmCommandFactory.name,
      ujson.Obj("prompt" -> prompt, "systemPrompt" -> system, "private-unknown-key" -> "private-extra-value"))
    val original = ujson.write(command.json)
    val expected = ujson.Obj("name" -> command.name, "json" -> ujson.Obj("redacted" -> true,
      "promptLength" -> prompt.length, "systemPromptLength" -> system.length))
    val (writes, logger) = logged(command, 1)
    val stored = writes.head
    assertEquals(ujson.read(stored(4)), expected)
    assertEquals(stored(3), command.name)
    assertEquals(stored(5), "user-unknown")
    assertEquals(stored(6), InetAddress.getLoopbackAddress.getHostAddress)
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
      val (writes, logger) = logged(command, 0)
      val expected = ujson.Obj("name" -> command.name, "json" -> ujson.Obj("redacted" -> true,
        "promptLength" -> ujson.Null, "systemPromptLength" -> ujson.Null))
      assertEquals(ujson.read(writes.head(4)), expected)
      assert(logger.getOut().contains("Failed db logging for command:"))
      assert(!logger.getOut().contains("private"))
      assert(!logger.getErr().contains("private"))
      assertEquals(ujson.write(command.json), original)
    }
  }

  test("logging retains the original payload and failure warning for other commands") {
    val command = ExecutionCommand("another-command", ujson.Obj("prompt" -> "unchanged payload 学校"))
    val (writes, logger) = logged(command, 0)
    assertEquals(writes.head(4), command.toJson)
    assertEquals(CommandLogWriter.payloadForLog(command), command.toJson)
    assert(logger.getOut().contains("Failed db logging for command: " + command.name + " (" + command.toJson + ")"))
  }

  test("without a configured database no log is written and the command is not delayed") {
    val command = ExecutionCommand("another-command", ujson.Obj())
    val context = CommandAccess.RequestContext(Instant.now(), InetAddress.getLoopbackAddress, None)
    assert(CommandLogWriter(None).record(command, context, BasicLogger()).isCompleted)
  }
}
