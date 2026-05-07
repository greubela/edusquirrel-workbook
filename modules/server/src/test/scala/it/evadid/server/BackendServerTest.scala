package it.evadid.server

import it.evadid.distribution.ExecutionCommand
import munit.FunSuite
import upickle.default.write

class BackendServerTest extends FunSuite {

  test("onExecuteCommandReceived executes valid command") {
    val command = ExecutionCommand("build", Map("target" -> "test"))

    val result = BackendServer.onExecuteCommandReceived(write(command))

    assertEquals(result.command.name, "build")
    assert(result.result.isSuccess)
  }

  test("onExecuteCommandReceived rejects empty command name") {
    val command = ExecutionCommand("   ", Map.empty)
    intercept[IllegalArgumentException] {
      BackendServer.onExecuteCommandReceived(write(command))
    }
  }
}
