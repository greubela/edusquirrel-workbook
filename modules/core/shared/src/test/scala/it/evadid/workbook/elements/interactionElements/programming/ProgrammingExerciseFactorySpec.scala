package it.evadid.workbook.elements.interactionElements.programming
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStateJavaString
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*

import it.evadid.vm.test.{BeTestSuite, SampleBeTest}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
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

  test("legacy full Java JSON without a turtle task retains its IDs, test suite and exact default source") {
    val defaultSource = "public class Main {\n  public static void main(String[] args) {\n  }\n}\n"
    val testSuite: Option[BeTestSuite] = Some(SampleBeTest("assert True\n"))
    val legacyPayloads = List(
      (
        """{"elementId":"prog-full-java","elementType":"ProgrammingExerciseFullJava","allConstructorFields":{"testSuite":null}}""",
        "prog-full-java",
        Option.empty[BeTestSuite]
      ),
      (
        """{"elementId":"full-java","elementType":"ProgrammingExerciseFullJava","allConstructorFields":{}}""",
        "full-java",
        Option.empty[BeTestSuite]
      ),
      (
        upickle.default.write(
          WorkbookElementSerializable(
            "full-java-with-suite",
            "ProgrammingExerciseFullJava",
            Map("testSuite" -> upickle.default.writeJs(testSuite))
          )
        )(using WorkbookElementSerializable.regularSerializer),
        "full-java-with-suite",
        testSuite
      )
    )

    legacyPayloads.foreach { (json, expectedId, expectedSuite) =>
      val restored = WorkbookElementFactory.serializerRefBasedJson.deserialize(json)
        .asInstanceOf[ProgrammingExerciseFullJava]
      assertEquals(restored.elementId, expectedId)
      assertEquals(restored.testSuite, expectedSuite)
      assertEquals(restored.startingProgram, defaultSource)
      assertEquals(restored.defaultValue, ProgrammingStateJavaString(defaultSource))
    }
  }

  test("full Java exercises roundtrip their exact starting source without task grading") {
    val source = " \r\npublic class Drawing {\r\n  public static void main(String[] args) {\n    square(25);\n  }\r\n}\n\n\t "
    val testSuite: Option[BeTestSuite] = Some(SampleBeTest("  assert True\r\n"))
    val original = ProgrammingExerciseFullJava("prog-full-java-source", testSuite, source)
    val serialized = ProgrammingExerciseFullJava.factory.toSerializableElement(original)
    assertEquals(serialized.getElement("startingProgram"), source)
    assert(!serialized.allConstructorFields.contains("turtleTask"))

    val restored = WorkbookElementFactory.serializerRefBasedJson.deserialize(
      WorkbookElementFactory.serializerRefBasedJson.serialize(original)
    ).asInstanceOf[ProgrammingExerciseFullJava]
    assertEquals(restored.elementId, original.elementId)
    assertEquals(restored.testSuite, testSuite)
    assertEquals(restored.startingProgram, source)
    assertEquals(restored.defaultValue, ProgrammingStateJavaString(source))
  }

  test("legacy full Java task definitions migrate only their exact starting source") {
    val source = " \r\npublic class Drawing {\n  static void square(int side) {}\r\n}\t "
    val suite: Option[BeTestSuite] = Some(SampleBeTest("assert True\n"))
    val legacyTask = ujson.Obj(
      "startingProgram" -> source,
      "methodName" -> "square",
      "cases" -> ujson.Arr(),
      "comparisonPolicy" -> "removed-policy"
    )
    val legacy = WorkbookElementSerializable(" java-square-pilot ", "ProgrammingExerciseFullJava",
      Map("testSuite" -> upickle.default.writeJs(suite),
        "turtleTask" -> upickle.default.writeJs[Option[ujson.Value]](Some(legacyTask))))
    val restored = WorkbookElementFactory.parse(legacy).asInstanceOf[ProgrammingExerciseFullJava]
    assertEquals(restored.elementId, legacy.elementId)
    assertEquals(restored.testSuite, suite)
    assertEquals(restored.startingProgram, source)
    assertEquals(restored.defaultValue, ProgrammingStateJavaString(source))

    val rewritten = restored.toSerialized
    assert(!rewritten.allConstructorFields.contains("turtleTask"))
    assertEquals(rewritten.getElement("startingProgram"), source)
    val savedDraft = ProgrammingStateJavaString(" \r\npublic class Drawing {\n  static void square(\r\n\t ")
    val saved = restored.serializerInteractionContent.serialize(savedDraft)
    assertEquals(restored.serializerInteractionContent.deserialize(saved), savedDraft)
    assertEquals(ProgrammingState.fingerprint(restored.serializerInteractionContent.deserialize(saved)),
      ProgrammingState.fingerprint(savedDraft))
  }

  test("explicit full Java starting source takes precedence over a legacy task definition") {
    val source = " \r\npublic class Restored {\n\t "
    val legacy = WorkbookElementSerializable("java-source-priority", "ProgrammingExerciseFullJava",
      Map("startingProgram" -> ujson.Str(source), "turtleTask" -> ujson.Bool(false)))
    val restored = WorkbookElementFactory.parse(legacy).asInstanceOf[ProgrammingExerciseFullJava]
    assertEquals(restored.startingProgram, source)
    assertEquals(restored.defaultValue, ProgrammingStateJavaString(source))
  }

  test("malformed legacy full Java starter source cannot silently become a different program") {
    List(ujson.Obj(), ujson.Obj("startingProgram" -> 25)).foreach { task =>
      val legacy = WorkbookElementSerializable("java-invalid-starter", "ProgrammingExerciseFullJava",
        Map("turtleTask" -> upickle.default.writeJs[Option[ujson.Value]](Some(task))))
      intercept[Exception](WorkbookElementFactory.parse(legacy))
    }
  }

  test("a full Java exercise preserves unfinished student source independently of its starter") {
    val source = "public class Drawing {\n  static void square(int side) {}\n}\n"
    val exercise = ProgrammingExerciseFullJava("prog-full-java-draft", startingProgram = source)
    val invalidDraft = ProgrammingStateJavaString(" \r\npublic class Drawing {\n  public static void square(int side) {\r\n    forward(\n\n\t ")
    val serialized = exercise.serializerInteractionContent.serialize(invalidDraft)
    val restored = exercise.serializerInteractionContent.deserialize(serialized)
    assertEquals(restored, invalidDraft)
    assertEquals(ProgrammingState.fingerprint(restored), ProgrammingState.fingerprint(invalidDraft))
    assertEquals(exercise.defaultValue, ProgrammingStateJavaString(source))
  }
}
