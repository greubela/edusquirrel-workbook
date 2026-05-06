package it.evadid.worker

import it.evadid.distribution.ExecutionCommand
import it.evadid.distribution.ExecutionCommand.ExecutionInfo
import it.evadid.distribution.{ExecutionCommand as Cmd, Executor}
import munit.FunSuite
import upickle.default.write

import java.time.LocalDateTime
import scala.util.Success

class BackendWorkerTest extends FunSuite {

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

  test("worker backend executes supported command") {
    BackendWorker.executors = List(configuredExecutor(_.name == "build", "first"))
    val command = ExecutionCommand("build", Map("target" -> "test"))

    val result = BackendWorker.onExecuteCommandReceived(write(command))

    assertEquals(result.result.get.data.get("handledBy"), Some("first"))
  }

  test("worker backend with no executors returns meaningful error") {
    BackendWorker.executors = Nil
    val command = ExecutionCommand("build", Map.empty)

    val error = intercept[IllegalStateException] {
      BackendWorker.onExecuteCommandReceived(write(command))
    }
    assert(error.getMessage.contains("No executor available for command 'build'"))
  }
}
