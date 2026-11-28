package it.evadid.core.util.io

import it.evadid.workbook.model.history.InteractionVariableHistory
import it.evadid.workbook.sync.model.InteractionHistoryJson
import munit.FunSuite
import upickle.default.*

import it.evadid.workbook.abstractions.InteractionFormat.*

class ParsingContextSpec extends FunSuite {
  test("typed history failures identify the entity, trimmed input, and underlying reason") {
    val input = ujson.Obj()
    val error = intercept[Exception] { InteractionHistoryJson(TextInteractionFormat, input.toString).toTyped }
    val reason = ParsingContext.reason(error)
    assert(reason.contains("interaction history"))
    assert(reason.contains("events"), reason)
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
