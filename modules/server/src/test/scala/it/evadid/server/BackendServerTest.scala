package it.evadid.server

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.util.logging.BasicLogger
import munit.FunSuite

import java.net.InetAddress
import java.time.LocalDateTime

class BackendServerTest extends FunSuite {
  test("BackendCommandHandler rejects empty command name") {
    val command = ExecutionCommand("   ", Map.empty)

    intercept[IllegalArgumentException] {
      BackendCommandHandler.handleExecution(
        LocalDateTime.parse("2026-01-01T08:00:00"),
        command,
        None,
        InetAddress.getLoopbackAddress,
        BasicLogger()
      )
    }
  }
}
