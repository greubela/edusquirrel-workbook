import it.evadid.distribution.ExecutionCommand
import it.evadid.worker.WebWorkerBackendServer
import munit.FunSuite
import upickle.default.write

class BackendWorkerTest extends FunSuite {

  test("worker backend echoes params and succeeds") {
    val command = ExecutionCommand("build", Map("target" -> "test"))

    val result = WebWorkerBackendServer.onExecuteCommandReceived(write(command))

    assertEquals(result.command.name, "build")
    assertEquals(result.result.get.data.get("target"), Some("test"))
    assert(result.result.isSuccess)
  }
}
