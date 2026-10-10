package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingExerciseFullJava}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import munit.FunSuite

class ProgrammingExerciseFactorySpec extends FunSuite {

  test("factory roundtrips editorPalette and referencePython") {
    val original = ProgrammingExercise(
      "prog-ref",
      testSuite = None,
      editorPalette = ProgrammingEditorPalette.BeginnerTurtle,
      referencePython = Some(
        """for i in range(4):
          |    forward(100)
          |    left(90)
          |""".stripMargin
      )
    )
    val serialized = ProgrammingExercise.factory.toSerializableElement(original)
    assertEquals(
      serialized.getElementAs[ProgrammingEditorPalette]("editorPalette"),
      ProgrammingEditorPalette.BeginnerTurtle
    )
    assertEquals(
      serialized.getElementAs[Option[String]]("referencePython"),
      original.referencePython
    )

    val restored = ProgrammingExercise.factory.fromSerializedElement(serialized, Map.empty)
    assertEquals(restored.elementId, "prog-ref")
    assertEquals(restored.editorPalette, ProgrammingEditorPalette.BeginnerTurtle)
    assertEquals(restored.referencePython, original.referencePython)
    assertEquals(restored.testSuite, None)
  }

  test("factory defaults missing optional fields for legacy payloads") {
    val legacy = it.evadid.workbook.jsonFactory.WorkbookElementSerializable(
      "prog-legacy",
      "ProgrammingExercise",
      Map("elementId" -> upickle.default.writeJs("prog-legacy"))
    )
    val restored = ProgrammingExercise.factory.fromSerializedElement(legacy, Map.empty)
    assertEquals(restored.editorPalette, ProgrammingEditorPalette.Default)
    assertEquals(restored.referencePython, None)
    assertEquals(restored.testSuite, None)
  }

  test("full Java exercise is registered and preserves its type and test suite") {
    val original = ProgrammingExerciseFullJava("full-java")
    val serialized = it.evadid.workbook.jsonFactory.WorkbookElementFactory.serializerRefBasedJson.serialize(original)
    val restored =
      it.evadid.workbook.jsonFactory.WorkbookElementFactory.serializerRefBasedJson.deserialize(serialized)

    assertEquals(restored.getClass.getSimpleName, "ProgrammingExerciseFullJava")
    val fullJava = restored.asInstanceOf[ProgrammingExerciseFullJava]
    assertEquals(fullJava.elementId, original.elementId)
    assertEquals(fullJava.testSuite, None)
    assertEquals(fullJava.defaultValue, original.defaultValue)
    assert(fullJava.defaultValue.toJava.code.contains("class Main"))
    assertEquals(
      fullJava.serializerInteractionContent.deserialize(fullJava.serializerInteractionContent.serialize(fullJava.defaultValue)),
      fullJava.defaultValue
    )
  }
}
