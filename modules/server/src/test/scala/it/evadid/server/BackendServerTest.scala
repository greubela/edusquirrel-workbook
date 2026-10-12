package it.evadid.server

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.util.logging.BasicLogger
import munit.FunSuite

import java.net.InetAddress
import java.time.LocalDateTime

class BackendServerTest extends FunSuite {
  test("BackendCommandHandler rejects empty command name") {
    val command = ExecutionCommand("   ", ujson.Obj())

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
  test("server failures use the response envelope understood by execution clients") {
    val time = LocalDateTime.parse("2026-01-01T08:00:00")
    val logger = BasicLogger()
    val command = ExecutionCommand("invalid", ujson.Obj())
    val response = BackendServer.fail(time, "Could not parse command", None, Some(command), logger)
    val restored = upickle.default.read[it.evadid.distribution.formats.ExecutionClientResponse](upickle.default.write(response))
    assertEquals(restored.receivedExecutionCommand, Some(command))
    assertEquals(restored.timestamps.timestampReceived, time)
    assertEquals(restored.result.left.toOption.map(_.msg), Some("Could not parse command"))
    assert(restored.parse[String](logger, command, time).isInstanceOf[it.evadid.distribution.command.ExecutionInfo.ExecutionFailure[?]])
  }

}
