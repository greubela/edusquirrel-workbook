package it.evadid.distribution.commandTypes

import it.evadid.distribution.commandTypes.LLMCommands.FeedbackLlmRequest
import munit.FunSuite
import upickle.default.*

class FeedbackLlmRequestSpec extends FunSuite {
  test("malformed feedback requests fail without retaining a payload-bearing cause") {
    val payloads = List(ujson.Obj(), ujson.Obj("prompt" -> "private source"),
      ujson.Obj("prompt" -> "private source", "systemPrompt" -> false),
      ujson.Obj("prompt" -> ujson.Null, "systemPrompt" -> "private instructions"),
      ujson.Obj("prompt" -> "private source", "systemPrompt" -> ujson.Null),
      ujson.Obj("prompt" -> 42, "systemPrompt" -> "private instructions"),
      ujson.Obj("prompt" -> false, "systemPrompt" -> "private instructions"),
      ujson.Obj("prompt" -> "private source", "systemPrompt" -> ujson.Obj("private" -> "value")),
      ujson.Obj("prompt" -> ujson.Arr("private source"), "systemPrompt" -> "private instructions"),
      ujson.Str("private non-object source"), ujson.Num(42), ujson.Arr("private array"), ujson.Null)
    payloads.foreach { payload =>
      val error = intercept[IllegalArgumentException](read[FeedbackLlmRequest](payload))
      assertEquals(error.getMessage, "Invalid feedback request.", ujson.write(payload))
      assertEquals(error.getCause, null)
      // Text input adds only the JSON path; the cause is the same fixed rejection.
      val fromText = intercept[Exception](read[FeedbackLlmRequest](ujson.write(payload)))
      val root = Iterator.iterate[Throwable](fromText)(_.getCause).takeWhile(_ != null).toList.last
      assertEquals(root.getMessage, "Invalid feedback request.")
      assert(!Iterator.iterate[Throwable](fromText)(_.getCause).takeWhile(_ != null).exists(_.getMessage.contains("private")))
    }
  }

  test("valid feedback requests round-trip, ignoring unknown fields") {
    val request = FeedbackLlmRequest("private source 学校", "", None, programmingHint = true)
    assertEquals(read[FeedbackLlmRequest](write(request)), request)
    val withExtra = ujson.Obj("prompt" -> "", "systemPrompt" -> "", "private extra key" -> "value")
    assertEquals(read[FeedbackLlmRequest](withExtra), FeedbackLlmRequest("", ""))
  }
}
