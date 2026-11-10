package it.evadid.core.util.io

import it.evadid.vm.parsing.java.clean.model.JavaType.{JAVA_ANY, JAVA_UNPARSABLE_TYPE}
import it.evadid.vm.parsing.python.clean.model.PythonType.{PYTHON_ANY, PYTHON_UNPARSABLE_TYPE}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory, InteractionVariableScaffoldingHistory}
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

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
      FeedbackEntity.SystemEntity(), Some(payload), LocalDateTime.of(2026, 11, 10, 12, 0))
    val history = InteractionVariableScaffoldingHistory(Map("draft" -> result))
    assertEquals(read[InteractionVariableScaffoldingHistory[String, ujson.Value]](write(history)), history)
    assertEquals(readBinary[InteractionVariableScaffoldingHistory[String, ujson.Value]](writeBinary(history)), history)
  }

  test("interactions without grading or scaffolding information use Unit payloads") {
    val value: InteractionVariable[String, Unit, Unit] = InteractionVariable(TextInteraction("typed-answer"))
    val history = value.currentHistory
    assertEquals(read[InteractionVariableHistory[String, Unit, Unit]](write(history)), history)
    assertEquals(readBinary[InteractionVariableHistory[String, Unit, Unit]](writeBinary(history)), history)
  }

  test("unknown language literals preserve opaque source text with a String codec") {
    val source = "  customCall(\"quoted\", other)  "
    val codecs = List(new JAVA_ANY().serializerScalaValue, new JAVA_UNPARSABLE_TYPE("Custom").serializerScalaValue,
      new PYTHON_ANY().serializerScalaValue, new PYTHON_UNPARSABLE_TYPE("Custom").serializerScalaValue)
    codecs.foreach { codec =>
      assertEquals(codec.serialize(source), source)
      assertEquals(codec.deserialize(source), source)
    }
  }
}
