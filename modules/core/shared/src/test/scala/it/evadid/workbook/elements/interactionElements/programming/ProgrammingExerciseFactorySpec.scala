package it.evadid.workbook.elements.interactionElements.programming

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
    assertEquals(restored.allowedEditors, List(ProgrammingEditorKind.Snap))
  }

  test("factory roundtrips allowedEditors") {
    val original = ProgrammingExercise(
      "prog-both",
      allowedEditors = List(ProgrammingEditorKind.Snap, ProgrammingEditorKind.Python)
    )
    val serialized = ProgrammingExercise.factory.toSerializableElement(original)
    val restored = ProgrammingExercise.factory.fromSerializedElement(serialized, Map.empty)
    assertEquals(restored.allowedEditors, List(ProgrammingEditorKind.Snap, ProgrammingEditorKind.Python))
  }

  test("python editor forces a python-compatible palette") {
    val native = ProgrammingExercise(
      "prog-native",
      editorPalette = ProgrammingEditorPalette.Default,
      allowedEditors = List(ProgrammingEditorKind.Snap, ProgrammingEditorKind.Python)
    )
    assertEquals(native.effectiveEditorPalette, ProgrammingEditorPalette.PythonCompatibleSnap)

    val beginner = native.copy(editorPalette = ProgrammingEditorPalette.BeginnerTurtle)
    assertEquals(beginner.effectiveEditorPalette, ProgrammingEditorPalette.BeginnerTurtle)

    val embroidery = native.copy(editorPalette = ProgrammingEditorPalette.Embroidery)
    assertEquals(embroidery.effectiveEditorPalette, ProgrammingEditorPalette.Embroidery)

    val snapOnly = ProgrammingExercise("prog-snap", editorPalette = ProgrammingEditorPalette.Default)
    assertEquals(snapOnly.effectiveEditorPalette, ProgrammingEditorPalette.Default)
  }
}
