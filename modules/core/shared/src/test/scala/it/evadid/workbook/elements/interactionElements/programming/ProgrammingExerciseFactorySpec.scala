package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.vm.test.{BeTestSuite, SampleBeTest}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import JavaTurtleArgument.{IntValue, DoubleValue}
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
          List(IntValue(25), IntValue(-3)),
          TurtleGraphic.TurtleGraphicProgram(List(
            TurtleCommand("forward", List(25.0)),
            TurtleCommand("turnRight", List(90.0))
          ))
        ),
        JavaTurtleCase(List(IntValue(40)), TurtleGraphic.TurtleGraphicSvgString("M0 0 L40 0 L40 40 Z")),
        JavaTurtleCase(List(IntValue(0)), TurtleGraphic.TurtleGraphicProgram(Nil))
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
    assertEquals(restored.turtleTask.get.cases.map(_.arguments),
      List(List(IntValue(25), IntValue(-3)), List(IntValue(40)), List(IntValue(0))))
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
    assertEquals(task.cases.map(_.arguments), List(List(IntValue(25)), List(IntValue(40)), List(IntValue(0))))

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

  test("legacy turtle case arguments retain their numeric JSON representation") {
    val target: TurtleGraphic = TurtleGraphic.TurtleGraphicProgram(List(TurtleCommand("forward", List(25.0))))
    val numbers = ujson.Arr(Int.MinValue, -3, 0, 25, Int.MaxValue)
    val payload = ujson.Obj("arguments" -> numbers, "expectedShape" -> upickle.default.writeJs(target))
    val restored = upickle.default.read[JavaTurtleCase](payload)

    assertEquals(restored.arguments, List(Int.MinValue, -3, 0, 25, Int.MaxValue).map(IntValue.apply))
    assertEquals(restored.expectedShape, target)
    val rewritten = upickle.default.writeJs(restored)
    assertEquals(rewritten("arguments"), numbers)
    assertEquals(rewritten.obj.keySet.toSet, Set("arguments", "expectedShape"))
    assert(restored.arguments.forall(_.isValid))
    assertEquals(upickle.default.read[JavaTurtleArgument]("2.0"), IntValue(2))
  }

  test("mixed turtle task arguments roundtrip without narrowing or changing student source") {
    val source = " \r\npublic class Drawing {\n  static void draw(int depth, double length) {}\r\n  public static void main(String[] args) {}\n}\t "
    val target: TurtleGraphic = TurtleGraphic.TurtleGraphicProgram(Nil)
    val task = JavaTurtleTask(source, "draw", List(
      JavaTurtleCase(List(IntValue(2), DoubleValue(10.5)), target),
      JavaTurtleCase(List(IntValue(0), DoubleValue(2.0)), target),
      JavaTurtleCase(List(IntValue(1), DoubleValue(-0.0)), target)
    ))
    val exercise = ProgrammingExerciseFullJava("java-mixed-arguments", turtleTask = Some(task))
    val restored = WorkbookElementFactory.serializerRefBasedJson.deserialize(
      WorkbookElementFactory.serializerRefBasedJson.serialize(exercise)
    ).asInstanceOf[ProgrammingExerciseFullJava]

    assertEquals(restored.elementId, exercise.elementId)
    assertEquals(restored.defaultValue, ProgrammingStateJavaString(source))
    assertEquals(restored.turtleTask, Some(task))
    val arguments = restored.turtleTask.get.cases.map(_.arguments)
    assertEquals(arguments(1), List(IntValue(0), DoubleValue(2.0)))
    arguments.last.last match {
      case DoubleValue(value) => assertEquals(java.lang.Double.doubleToRawLongBits(value), Long.MinValue)
      case other => fail(s"Expected a double argument, got $other")
    }
    val serialized = upickle.default.writeJs(task)
    assertEquals(serialized("cases")(0)("arguments")(0), ujson.Num(2))
    assertEquals(serialized("cases")(0)("arguments")(1), ujson.Obj("type" -> "double", "value" -> 10.5))
    assertEquals(serialized("cases")(2)("arguments")(1), ujson.Obj("type" -> "double", "value" -> "-0.0"))
    assertEquals(arguments(1)(1).literal, "2.0")
    assertEquals(arguments.last.last.literal, "-0.0")
  }

  test("turtle numeric arguments preserve their type and floating-point bits") {
    val random = new scala.util.Random(20261008L)
    val samples = List.fill(256)(java.lang.Double.longBitsToDouble(random.nextLong())).filter(_.isFinite)
    val arguments: List[JavaTurtleArgument] = List(
      IntValue(Int.MinValue), IntValue(Int.MaxValue), IntValue(0),
      DoubleValue(0.0), DoubleValue(-0.0), DoubleValue(2.0), DoubleValue(10.0 / 3.0),
      DoubleValue(java.lang.Double.MIN_VALUE), DoubleValue(-java.lang.Double.MIN_VALUE),
      DoubleValue(java.lang.Double.longBitsToDouble(0x0010000000000000L)),
      DoubleValue(java.lang.Double.longBitsToDouble(0x3fefffffffffffffL)),
      DoubleValue(java.lang.Double.longBitsToDouble(0x3ff0000000000001L)), DoubleValue(java.lang.Double.MAX_VALUE),
      DoubleValue(-java.lang.Double.MAX_VALUE)
    ) ++ samples.map(DoubleValue.apply)
    arguments.foreach { argument =>
      assert(argument.isValid)
      val restored = upickle.default.read[JavaTurtleArgument](upickle.default.write(argument))
      (argument, restored) match {
        case (DoubleValue(expected), DoubleValue(actual)) =>
          assertEquals(java.lang.Double.doubleToRawLongBits(actual), java.lang.Double.doubleToRawLongBits(expected))
        case _ => assertEquals(restored, argument)
      }
    }
  }

  test("turtle argument readers reject invalid integers and malformed double values") {
    val invalid = List[ujson.Value](
      ujson.Num(2.5), ujson.Num(Int.MaxValue.toDouble + 1), ujson.Num(Int.MinValue.toDouble - 1),
      ujson.Num(1e100), ujson.Num(Double.NaN), ujson.Num(Double.PositiveInfinity),
      ujson.Num(Double.NegativeInfinity), ujson.Str("2"), ujson.Bool(true), ujson.Null, ujson.Arr(2),
      ujson.Obj(), ujson.Obj("type" -> "double"), ujson.Obj("value" -> 2),
      ujson.Obj("type" -> "int", "value" -> 2), ujson.Obj("type" -> 2, "value" -> 2),
      ujson.Obj("type" -> "double", "value" -> "2.5"),
      ujson.Obj("type" -> "double", "value" -> "0.0"),
      ujson.Obj("type" -> "double", "value" -> "-0"),
      ujson.Obj("type" -> "double", "value" -> "-0.00"),
      ujson.Obj("type" -> "double", "value" -> ujson.Null),
      ujson.Obj("type" -> "double", "value" -> true),
      ujson.Obj("type" -> "double", "value" -> 2.5, "unit" -> "pixels"),
      ujson.Obj("type" -> "double", "value" -> ujson.Num(Double.NaN)),
      ujson.Obj("type" -> "double", "value" -> ujson.Num(Double.PositiveInfinity)),
      ujson.Obj("type" -> "double", "value" -> ujson.Num(Double.NegativeInfinity))
    )
    invalid.foreach { value =>
      intercept[Exception] { upickle.default.read[JavaTurtleArgument](value) }
    }
    intercept[Exception] { upickle.default.read[JavaTurtleArgument]("1e309") }
    intercept[Exception] { upickle.default.read[JavaTurtleArgument]("""{"type":"double","value":1e309}""") }
  }

  test("turtle argument writers reject nonfinite doubles") {
    List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity).foreach { number =>
      val argument: JavaTurtleArgument = DoubleValue(number)
      assert(!argument.isValid)
      intercept[Exception] { upickle.default.write(argument) }
    }
  }
}
