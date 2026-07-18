package it.evadid.server

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.util.logging.BasicLogger
import munit.FunSuite

import java.time.LocalDateTime
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class BackendServerTest extends FunSuite {
  test("BackendCommandHandler rejects empty command name") {
    val command = ExecutionCommand("   ", Map.empty)

    intercept[IllegalArgumentException] {
      Await.result(BackendCommandHandler.handleExecution(LocalDateTime.parse("2026-01-01T08:00:00"), command, BasicLogger()), 5.seconds)
    }
  }
}
