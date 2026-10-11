package it.evadid.distribution.command

import upickle.default.*
import munit.FunSuite

import java.time.LocalDateTime

class ExecutionInfoIntegrationTest extends FunSuite {
  test("ExecutionInfo JSON roundtrip preserves command and result") {
    val command = ExecutionCommand("math", ujson.Obj("a" -> "1"))
    val history = ExecutionHistory(LocalDateTime.parse("2026-01-01T08:00:00"), LocalDateTime.parse("2026-01-01T08:00:01"), LocalDateTime.parse("2026-01-01T08:00:02"), LocalDateTime.parse("2026-01-01T08:00:03"))
    val info = ExecutionInfo.ExecutionSuccess(command, history, Map("sum" -> "2"))

    val restored = read[ExecutionInfo[Map[String, String]]](write[ExecutionInfo[Map[String, String]]](info))

    assertEquals(restored.command, command)
    assertEquals(restored.successOrThrow().result, Map("sum" -> "2"))
  }

  test("fixTime adds explicit timeline when no meta exists") {
    val requested = LocalDateTime.parse("2026-01-01T08:00:00")
    val received = LocalDateTime.parse("2026-01-01T08:00:01")
    val history = ExecutionHistory(requested.plusSeconds(10), received.plusSeconds(10), requested.plusSeconds(11), received.plusSeconds(11))
    val info = ExecutionInfo.ExecutionSuccess(ExecutionCommand("noop", ujson.Obj()), history, Map.empty[String, String])

    val fixed = info.withFixedTime(requested, received)

    assertEquals(fixed.successOrThrow().history.timestampCommandRequested, requested)
    assertEquals(fixed.successOrThrow().history.timestampCommandReceived, received)
  }
}
