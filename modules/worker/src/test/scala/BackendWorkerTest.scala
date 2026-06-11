import it.evadid.distribution.command.ExecutionCommand
import it.evadid.worker.WebWorkerBackendServer
import munit.FunSuite

class BackendWorkerTest extends FunSuite {

  test("worker backend echoes params and succeeds") {
    val command = ExecutionCommand("build", Map("target" -> "test"))

    val result = WebWorkerBackendServer.onExecuteCommandReceived(command.toJson)

    assertEquals(result.command.name, "build")
    assertEquals(result.result.get.data.get("target"), Some("test"))
    assert(result.result.isSuccess)
  }
}
