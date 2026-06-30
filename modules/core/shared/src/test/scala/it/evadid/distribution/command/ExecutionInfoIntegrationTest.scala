package it.evadid.distribution.command

import munit.FunSuite

import java.time.LocalDateTime
import scala.util.Success

class ExecutionInfoIntegrationTest extends FunSuite {
  test("ExecutionInfo JSON roundtrip preserves command and result") {
    val command = ExecutionCommand("math", Map("a" -> "1"))
    val info = ExecutionInfo(command, Success(ExecutionResult(Map("sum" -> "2"), "done", "")), None)

    val restored = ExecutionInfo.fromJson(info.toJson)

    assertEquals(restored.command, command)
    assertEquals(restored.resultTry.get.data, Map("sum" -> "2"))
  }

  test("fixTime adds explicit timeline when no meta exists") {
    val requested = LocalDateTime.parse("2026-01-01T08:00:00")
    val received = LocalDateTime.parse("2026-01-01T08:00:01")
    val info = ExecutionInfo(ExecutionCommand("noop", Map.empty), Success(ExecutionResult(Map.empty, "", "")), None)

    val fixed = info.fixTime(requested, received)

    assertEquals(fixed.historyOp.get.timestampCommandRequested, requested)
    assertEquals(fixed.historyOp.get.timestampCommandReceived, received)
  }
}
