package it.evadid.server

import it.evadid.distribution.ExecutionCommand
import it.evadid.distribution.ExecutionCommand.ExecutionInfo
import it.evadid.distribution.{ExecutionCommand as Cmd, Executor}
import munit.FunSuite
import upickle.default.write

import java.time.LocalDateTime
import scala.util.Success

class BackendServerTest extends FunSuite {

  private def infoFor(command: ExecutionCommand, marker: String): ExecutionInfo =
    ExecutionInfo(
      command,
      Success(Cmd.ExecutionResult(Map("handledBy" -> marker), s"$marker handled", "")),
      Some(Cmd.CommandHistory(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()))
    )

  private def configuredExecutor(canHandle: ExecutionCommand => Boolean, marker: String): Executor = new Executor {
    override def canExecute(executionCommand: ExecutionCommand): Boolean = canHandle(executionCommand)
    override def execute(executionCommand: ExecutionCommand): Option[ExecutionInfo] =
      if (canExecute(executionCommand)) Some(infoFor(executionCommand, marker)) else None
  }

  test("server with one executor executes supported command") {
    BackendServer.executors = List(configuredExecutor(_.name == "build", "first"))
    val command = ExecutionCommand("build", Map("target" -> "test"))

    val result = BackendServer.onExecuteCommandReceived(write(command))

    assertEquals(result.result.get.data.get("handledBy"), Some("first"))
  }

  test("server with two executors uses first matching executor") {
    var secondCalled = false
    val first = configuredExecutor(_.name == "build", "first")
    val second = new Executor {
      override def canExecute(executionCommand: ExecutionCommand): Boolean = executionCommand.name == "build"
      override def execute(executionCommand: ExecutionCommand): Option[ExecutionInfo] = {
        secondCalled = true
        Some(infoFor(executionCommand, "second"))
      }
    }

    BackendServer.executors = List(first, second)
    val command = ExecutionCommand("build", Map.empty)
    val result = BackendServer.onExecuteCommandReceived(write(command))

    assertEquals(result.result.get.data.get("handledBy"), Some("first"))
    assertEquals(secondCalled, false)
  }

  test("server with no executors returns meaningful error") {
    BackendServer.executors = Nil
    val command = ExecutionCommand("build", Map.empty)

    val error = intercept[IllegalStateException] {
      BackendServer.onExecuteCommandReceived(write(command))
    }
    assert(error.getMessage.contains("No executor available for command 'build'"))
  }

  test("onExecuteCommandReceived rejects empty command name") {
    BackendServer.executors = List(configuredExecutor(_ => true, "default"))
    val command = ExecutionCommand("   ", Map.empty)
    intercept[IllegalArgumentException] {
      BackendServer.onExecuteCommandReceived(write(command))
    }
  }
}
