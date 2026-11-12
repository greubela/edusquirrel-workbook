package it.evadid.distribution.command

import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.*
import it.evadid.util.logging.BasicLogger
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

class ExecutionClientResponseSpec extends FunSuite {
  private val time = LocalDateTime.parse("2026-11-12T12:00:00")
  private val timestamps = ExecutionClientResponseTimestamps(time, time, time)
  private val command = ExecutionCommand("test", ujson.Obj())

  test("a wrong result type identifies the serialized payload and retains the decode error") {
    val response = ExecutionClientResponse.forValue(timestamps, "text", command)
    val failure = intercept[SerializedException](response.successOrThrow[Int](BasicLogger(), command, time))
    assert(failure.msg.contains("was String"))
    assert(failure.cause.nonEmpty)
  }

  test("successful decoding and existing failures retain their value and identity") {
    val response = ExecutionClientResponse.forValue(timestamps, 42, command)
    assertEquals(response.successOrThrow[Int](BasicLogger(), command, time).result, 42)
    assert(response.throwIfContainsError eq response)
    val error = SerializedException("database failure")
    val failed = ExecutionClientResponse.forError(timestamps, error, command)
    assert(intercept[SerializedException](failed.throwIfContainsError) eq error)
    assert(intercept[SerializedException](failed.successOrThrow[Int](BasicLogger(), command, time)) eq error)
  }

  private case class FailingResult()
  test("serialization failures return an error envelope with their cause") {
    given ReadWriter[FailingResult] = readwriter[String].bimap(_ => throw IllegalStateException("writer failed"), _ => FailingResult())
    val response = ExecutionClientResponse.forValue(timestamps, FailingResult(), command)
    val error = response.result.left.toOption.get
    assert(error.msg.contains("serialize execution result"))
    assert(error.cause.exists(_.msg == "writer failed"))
    assertEquals(response.receivedExecutionCommand, Some(command))
  }
}
