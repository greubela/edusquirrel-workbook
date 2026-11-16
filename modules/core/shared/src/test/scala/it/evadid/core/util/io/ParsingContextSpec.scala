package it.evadid.core.util.io

import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.sync.model.InteractionInteractionHistoryJson
import munit.FunSuite
import upickle.default.*

class ParsingContextSpec extends FunSuite {
  test("typed history failures identify the entity, trimmed input, and underlying reason") {
    val input = ujson.Obj()
    val error = intercept[Exception] { read[InteractionVariableHistory[String, Unit, Unit]](input) }
    val reason = ParsingContext.reason(error)
    assert(reason.contains("interactionVariableHistory"))
    assert(reason.contains("historyStates"), reason)
    assertEquals(ParsingContext.preview("x" * 1000), "x" * 180 + "…")
  }

  test("serialization failures identify the object type and preserve the encoder cause") {
    val failure = IllegalArgumentException("invalid writer")
    val codec = readwriter[String].bimap[Int](_ => throw failure, _.toInt)
    val error = intercept[it.evadid.distribution.command.SerializedException] {
      Serializer.fromUpickleJson(codec).serialize(123)
    }
    assert(error.msg.contains("Error serializing"))
    assert(error.msg.contains("invalid writer"))
    assertEquals(error.cause.map(_.msg), Some(failure.getMessage))
  }

  test("history field errors include the parsing operation and preserve the cause") {
    val error = intercept[IllegalArgumentException] { InteractionHistoryJson.lastTimestamp("{}") }
    assert(error.getMessage.contains("interactionVariableHistory timestamps"))
    assert(error.getMessage.contains("historyStates"))
    assert(error.getCause != null)
  }

  test("path-only parser errors include the useful underlying cause") {
    val cause = IllegalArgumentException("Missing required field historyStates")
    val error = intercept[IllegalArgumentException] {
      ParsingContext("interactionVariableHistory", "{}") { throw Exception("$", cause) }
    }
    assert(error.getMessage.contains("Missing required field historyStates"))
    assert(error.getCause.getCause eq cause)
    assertEquals(ParsingContext.preview("a\nb"), "a b")
  }
}
