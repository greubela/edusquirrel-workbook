package it.evadid.distribution.commandTypes

import it.evadid.distribution.commandTypes.LLMCommands.FeedbackLlmRequest
import munit.FunSuite
import upickle.default.*

class FeedbackLlmRequestSpec extends FunSuite {
  /** The exception and its causes; upickle may wrap the rejection with the JSON path. */
  private def chain(error: Throwable): List[Throwable] = Iterator.iterate(error)(_.getCause).takeWhile(_ != null).toList

  test("malformed feedback requests fail with a fixed message and no payload-bearing cause") {
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
      List(() => read[FeedbackLlmRequest](payload), () => read[FeedbackLlmRequest](ujson.write(payload))).foreach { decode =>
        val causes = chain(scala.util.Try(decode()).failed.getOrElse(fail(s"decoded ${ujson.write(payload)}")))
        assertEquals(causes.last.getMessage, "Invalid feedback request.", ujson.write(payload))
        assert(causes.last.isInstanceOf[IllegalArgumentException])
        assert(!causes.exists(error => Option(error.getMessage).exists(_.contains("private"))), ujson.write(payload))
      }
    }
  }

  test("valid feedback requests round-trip, ignoring unknown fields") {
    val request = FeedbackLlmRequest("private source 学校", "", None, programmingHint = true)
    assertEquals(read[FeedbackLlmRequest](write(request)), request)
    val withExtra = ujson.Obj("prompt" -> "", "systemPrompt" -> "", "private extra key" -> "value")
    assertEquals(read[FeedbackLlmRequest](withExtra), FeedbackLlmRequest("", ""))
  }
}
