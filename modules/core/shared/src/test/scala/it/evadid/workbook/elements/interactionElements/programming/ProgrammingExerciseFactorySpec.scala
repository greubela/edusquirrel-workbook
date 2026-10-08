package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.vm.test.{BeTestSuite, SampleBeTest}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
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
      assertEquals(restored.turtleTask, None)
      assertEquals(restored.defaultValue, ProgrammingStateJavaString(defaultSource))
    }
  }

  test("full Java turtle tasks roundtrip their starter whitespace, method, arguments and target shapes") {
    val source = " \r\npublic class Drawing {\r\n  public static void main(String[] args) {\n    square(25);\n  }\r\n}\n\n\t "
    val task = JavaTurtleTask(
      startingProgram = source,
      methodName = "square",
      cases = List(
        JavaTurtleCase(
          List(25, -3),
          TurtleGraphic.TurtleGraphicProgram(List(
            TurtleCommand("forward", List(25.0)),
            TurtleCommand("turnRight", List(90.0))
          ))
        ),
        JavaTurtleCase(List(40), TurtleGraphic.TurtleGraphicSvgString("M0 0 L40 0 L40 40 Z")),
        JavaTurtleCase(List(0), TurtleGraphic.TurtleGraphicProgram(Nil))
      )
    )
    val testSuite: Option[BeTestSuite] = Some(SampleBeTest("  assert True\r\n"))
    val original = ProgrammingExerciseFullJava("prog-full-java-task", testSuite, Some(task))
    val serialized = ProgrammingExerciseFullJava.factory.toSerializableElement(original)

    assertEquals(serialized.getElementAs[Option[JavaTurtleTask]]("turtleTask"), Some(task))
    assertEquals(original.defaultValue, ProgrammingStateJavaString(source))

    val restored = WorkbookElementFactory.serializerRefBasedJson.deserialize(
      WorkbookElementFactory.serializerRefBasedJson.serialize(original)
    ).asInstanceOf[ProgrammingExerciseFullJava]
    assertEquals(restored.elementId, original.elementId)
    assertEquals(restored.testSuite, testSuite)
    assertEquals(restored.turtleTask, Some(task))
    assertEquals(restored.defaultValue, ProgrammingStateJavaString(source))
    assertEquals(restored.turtleTask.get.cases.map(_.arguments), List(List(25, -3), List(40), List(0)))
    assertEquals(restored.turtleTask.get.cases.map(_.expectedShape), task.cases.map(_.expectedShape))
  }

  test("the square pilot has an unfinished parameter method and separate positive and zero targets") {
    val task = JavaTurtleTask.squarePilot
    assertEquals(task.methodName, "square")
    assert(task.startingProgram.contains("public class Drawing"))
    assert(task.startingProgram.contains("static void square(int side)"))
    assert(task.startingProgram.contains("public static void main(String[] args)"))
    assert(task.startingProgram.contains("square(25);"))
    assert(!task.startingProgram.contains("forward("))
    assertEquals(task.cases.map(_.arguments), List(List(25), List(40), List(0)))

    task.cases.take(2).zip(List(25.0, 40.0)).foreach { (testCase, side) =>
      val expected = List.fill(4)(List(
        TurtleCommand("forward", List(side)),
        TurtleCommand("turnRight", List(90.0))
      )).flatten
      assertEquals(testCase.expectedShape.toTurtleProgram.toList, expected)
    }
    assertEquals(task.cases.last.expectedShape.toTurtleProgram.toList, List.empty[TurtleCommand[Double]])
  }

  test("a task-backed full Java exercise preserves unfinished student source independently of the starter") {
    val exercise = ProgrammingExerciseFullJava("prog-full-java-draft", turtleTask = Some(JavaTurtleTask.squarePilot))
    val invalidDraft = ProgrammingStateJavaString(" \r\npublic class Drawing {\n  public static void square(int side) {\r\n    forward(\n\n\t ")
    val serialized = exercise.serializerInteractionContent.serialize(invalidDraft)
    val restored = exercise.serializerInteractionContent.deserialize(serialized)

    assertEquals(restored, invalidDraft)
    assertEquals(ProgrammingState.fingerprint(restored), ProgrammingState.fingerprint(invalidDraft))
    assertEquals(exercise.defaultValue, ProgrammingStateJavaString(JavaTurtleTask.squarePilot.startingProgram))
  }
}
