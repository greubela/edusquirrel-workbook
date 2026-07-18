package it.evadid.distribution.command

import munit.FunSuite

import java.time.LocalDateTime

class ExecutionInfoIntegrationTest extends FunSuite {
  test("ExecutionInfo JSON roundtrip preserves command and result") {
    val command = ExecutionCommand("math", Map("a" -> "1"))
    val history = ExecutionHistory(LocalDateTime.parse("2026-01-01T08:00:00"), LocalDateTime.parse("2026-01-01T08:00:01"), LocalDateTime.parse("2026-01-01T08:00:02"), LocalDateTime.parse("2026-01-01T08:00:03"))
    val info = ExecutionInfo.ExecutionInfoUntyped(command, ExecutionResult(Map("sum" -> "2"), "done", ""), history)

    val restored = ExecutionInfo.fromJson(info.toJson)

    assertEquals(restored.command, command)
    assertEquals(restored.result.data, Map("sum" -> "2"))
  }

  test("fixTime adds explicit timeline when no meta exists") {
    val requested = LocalDateTime.parse("2026-01-01T08:00:00")
    val received = LocalDateTime.parse("2026-01-01T08:00:01")
    val history = ExecutionHistory(requested.plusSeconds(10), received.plusSeconds(10), requested.plusSeconds(11), received.plusSeconds(11))
    val info = ExecutionInfo.ExecutionInfoUntyped(ExecutionCommand("noop", Map.empty), ExecutionResult(Map.empty, "", ""), history)

    val fixed = info.withFixedTime(requested, received)

    assertEquals(fixed.history.timestampCommandRequested, requested)
    assertEquals(fixed.history.timestampCommandReceived, received)
  }
}
