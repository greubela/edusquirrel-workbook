import it.evadid.distribution.*
import it.evadid.distribution.executor.Executor
import munit.FunSuite
import upickle.default.write

import java.time.LocalDateTime
import scala.util.Success
/*
class BackendWorkerTest extends FunSuite {

  private def infoFor(command: ExecutionCommand, marker: String): ExecutionInfo =
    ExecutionInfo(
      command,
      Success(ExecutionResult(Map("handledBy" -> marker), s"$marker handled", "")),
      Some(ExecutionHistory(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()))
    )

  private def configuredExecutor(canHandle: ExecutionCommand => Boolean, marker: String): Executor = new Executor {
    override def canExecute(executionCommand: ExecutionCommand): Boolean = canHandle(executionCommand)

    override def execute(executionCommand: ExecutionCommand): Option[ExecutionInfo] =
      if (canExecute(executionCommand)) Some(infoFor(executionCommand, marker)) else None
  }

  test("worker backend executes supported command") {
    WebWorkerBackendServer.executors = List(configuredExecutor(_.name == "build", "first"))
    val command = ExecutionCommand("build", Map("target" -> "test"))

    val result = WebWorkerBackendServer.onExecuteCommandReceived(write(command))

    assertEquals(result.result.get.data.get("handledBy"), Some("first"))
  }

  test("worker backend with no executors returns meaningful error") {
    WebWorkerBackendServer.executors = Nil
    val command = ExecutionCommand("build", Map.empty)

    val error = intercept[IllegalStateException] {
      WebWorkerBackendServer.onExecuteCommandReceived(write(command))
    }
    assert(error.getMessage.contains("No executor available for command 'build'"))
  }
}*/
