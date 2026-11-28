package it.evadid.core.util.io

import it.evadid.workbook.support.OfflineController

import it.evadid.vm.parsing.java.clean.model.JavaType.{JAVA_ANY, JAVA_UNPARSABLE_TYPE}
import it.evadid.vm.parsing.python.clean.model.PythonType.{PYTHON_ANY, PYTHON_UNPARSABLE_TYPE}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.interactions.basic.TextInteraction
import it.evadid.workbook.history.InteractionVariableHistory
import it.evadid.workbook.abstractions.StateReference
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingRecord
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

import it.evadid.workbook.abstractions.InteractionFormat.*

class TypedPayloadCodecSpec extends FunSuite {
  test("the default codec imports do not provide a catch-all ReadWriter[Any]") {
    assert(compileErrors("""
      import it.evadid.core.util.io.serializer.DefaultSerializer.given
      summon[upickle.default.ReadWriter[Any]]
    """).nonEmpty)
  }

  test("dynamic scaffolding payloads retain JSON types and nested structure in saved history") {
    val payload: ujson.Value = ujson.Obj("number" -> 42, "bool" -> true, "nothing" -> ujson.Null,
      "nested" -> ujson.Arr("学校", 1.25, false, ujson.Obj("value" -> "text")))
    val result = ScaffoldingResult[String, ujson.Value]("draft", ScaffoldingStatus.ScaffoldingRequestedButNotFinished(),
      FeedbackEntity.SystemEntity, Some(payload), LocalDateTime.of(2026, 11, 10, 12, 0))
    val record = result.record(StateReference("draft"))
    assertEquals(read[ScaffoldingRecord[ujson.Value]](write(record)), record)
    assertEquals(readBinary[ScaffoldingRecord[ujson.Value]](writeBinary(record)), record)
  }

  test("interactions without grading or scaffolding information use Unit payloads") {
    val text = TextInteraction("typed-answer")
    val history = OfflineController.of(text).currentHistory(text)
    assertEquals(read[InteractionVariableHistory[TextInteractionFormat.type]](write(history)), history)
    assertEquals(readBinary[InteractionVariableHistory[TextInteractionFormat.type]](writeBinary(history)), history)
  }

  test("unknown language literals preserve opaque source text with a String codec") {
    val source = "  customCall(\"quoted\", other)  "
    val codecs = List(JAVA_ANY.serializerScalaValue, JAVA_UNPARSABLE_TYPE("Custom").serializerScalaValue,
      PYTHON_ANY.serializerScalaValue, PYTHON_UNPARSABLE_TYPE("Custom").serializerScalaValue)
    codecs.foreach { codec =>
      assertEquals(codec.serialize(source), source)
      assertEquals(codec.deserialize(source), source)
    }
  }
}
