package it.evadid.workbook.elements.interactionElements.programming
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStateJavaString
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*

import it.evadid.vm.test.{BeTestResult, BeTestSuite, SampleBeTest}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingExerciseFullJava}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import munit.FunSuite
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.scaffolding.ProgrammingHintScaffolder
import it.evadid.distribution.command.SerializedException
import upickle.default.*
import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStatePythonString

import scala.concurrent.{ExecutionContext, Future}

class ProgrammingExerciseFactorySpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  private def pythonClient(value: ujson.Value, inspect: WorkerCommands.RunPythonRequest => Unit = _ => ()): ExecutionClient =
    WorkerCommands.runPython.toLocalExecutionClient((request, _) => {
      inspect(request)
      Future.successful(WorkerCommands.RunPythonResult(value, "", "", Nil))
    })

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

  test("full Java exercises roundtrip their exact starting source and suite") {
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

  test("full Java hint configuration roundtrips and constructs a scaffolder owned by the restored interaction") {
    val source = " \r\npublic class Drawing {\r\n  static void square(int side) {\n  }\r\n}\t "
    val task = Some(LanguageMapContentId("test/java-task"))
    val suite: Option[BeTestSuite] = Some(SampleBeTest("assert True\n"))
    val original = ProgrammingExerciseFullJava("java-hints", suite, source, task)
    val encoded = write(original)
    val definition = original.toSerialized
    assertEquals(definition.allConstructorFields.keySet, Set("testSuite", "startingProgram", "hintTask"))
    assertEquals(definition.getElementAs[Option[LanguageMapContentId]]("hintTask"), task)
    assertEquals(read[ujson.Value](encoded).obj.keySet.toSet, Set("$type", "elementId", "testSuite", "startingProgram", "hintTask"))
    val restored = List(read[ProgrammingExerciseFullJava](encoded), readBinary[ProgrammingExerciseFullJava](writeBinary(original))) ++
      List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike)
        .map(serializer => serializer.deserialize(serializer.serialize(original)).asInstanceOf[ProgrammingExerciseFullJava])
    (original :: restored).foreach { exercise =>
      assertEquals(exercise.elementId, original.elementId)
      assertEquals(exercise.testSuite, suite)
      assertEquals(exercise.startingProgram, source)
      assertEquals(exercise.hintTask, task)
      val owner = exercise.scaffoldingElement.collect { case hint: ProgrammingHintScaffolder[?] => hint.interaction }
      assert(owner.exists(_ eq exercise))
      assertEquals(exercise.defaultValue, ProgrammingStateJavaString(source))
      val draft = ProgrammingStateJavaString(" \r\nclass Drawing {\n  static void square(\t ")
      assertEquals(exercise.serializerInteractionContent.deserialize(exercise.serializerInteractionContent.serialize(draft)), draft)
    }
    restored.foreach(exercise => assert(!(exercise.scaffoldingElement.get eq original.scaffoldingElement.get)))
  }

  test("legacy full Java definitions keep hints disabled without changing their serialized fields") {
    val original = ProgrammingExerciseFullJava("java-without-hints")
    assertEquals(original.scaffoldingElement, None)
    assert(!original.toSerialized.allConstructorFields.contains("hintTask"))
    val definitions = List(original.toSerialized, original.toSerialized.withMapAdded(Map("hintTask" -> ujson.Null)))
    val exercises = definitions.map(ProgrammingExerciseFullJava.factory.fromSerializedElement(_, Map.empty)) ++
      List(read[ProgrammingExerciseFullJava](write(original)), readBinary[ProgrammingExerciseFullJava](writeBinary(original)))
    exercises.foreach { restored =>
      assertEquals(restored, original)
      assertEquals(restored.hintTask, None)
      assertEquals(restored.scaffoldingElement, None)
      assert(!restored.toSerialized.allConstructorFields.contains("hintTask"))
    }
  }

  test("invalid Java hint configuration fails rather than silently disabling hints") {
    val original = ProgrammingExerciseFullJava("java-invalid-hints").toSerialized
    List(ujson.Num(5), ujson.Bool(false), ujson.Obj(), writeJs[Option[String]](Some("invalid"))).foreach { invalid =>
        intercept[SerializedException](ProgrammingExerciseFullJava.factory.fromSerializedElement(
          original.withMapAdded(Map("hintTask" -> invalid)), Map.empty))
      }
  }

  test("only configured suites create a single manual grader without changing exercise constructor fields") {
    val suite = SampleBeTest("assert True\n")
    val java = ProgrammingExerciseFullJava("java-unit-tests", Some(suite))
    val general = ProgrammingExercise("programming-unit-tests", Some(suite))
    val expected = List(ProgrammingUnitTestGrader(suite))
    assertEquals(java.gradingElements, expected)
    assertEquals(general.gradingElements, expected)
    assertEquals(ProgrammingExerciseFullJava("no-suite-java").gradingElements, Nil)
    assertEquals(ProgrammingExercise("no-suite-general").gradingElements, Nil)
    assertEquals(java.toSerialized.allConstructorFields.keySet, Set("testSuite"))
    List(java, general).foreach { exercise =>
      val restored = WorkbookElementFactory.serializerRefBasedJson.deserialize(
        WorkbookElementFactory.serializerRefBasedJson.serialize(exercise))
      val graders = restored match {
        case value: ProgrammingExerciseFullJava => value.gradingElements
        case value: ProgrammingExercise => value.gradingElements
        case _ => fail("Unexpected exercise type")
      }
      assertEquals(graders, expected)
      assertEquals(graders.head.runningBehavior, GraderRunningBehavior.RUN_ONLY_MANUALLY)
    }
  }

  test("unit tests send exact Python source and assertions only on a forced grade") {
    val source = ProgrammingStatePythonString("def add(a, b):\r\n    return a + b\n")
    val suite = SampleBeTest("assert add(1, 2) == 3\n")
    val calls = State(0)
    val client = pythonClient(ujson.Obj("status" -> "passed", "reason" -> ""), request => {
      calls.update(_ + 1)
      assertEquals(request.context("_eva_source"), ujson.Str(source.code))
      assertEquals(request.context("_eva_tests"), ujson.Str(suite.pythonTestCode))
      assertEquals(request.context("_eva_is_java"), ujson.Bool(false))
      assertEquals(request.context("_eva_java_methods"), ujson.Obj())
      assert(request.code.contains("sys.settrace(trace)") && request.code.contains("finally:"))
      assert(request.code.contains("event in (\"line\", \"opcode\")"))
      assert(request.code.contains("compile(tree, \"<workbook-tests>\", \"exec\", optimize=0)"))
      assert(request.code.contains("compile(source, \"<student-source>\", \"exec\", optimize=0)"))
      assert(request.resetGlobals && !request.captureStdout && !request.captureStderr)
      assertEquals(request.callbacks, Map.empty[String, List[String]])
      assertEquals(request.packages, Nil)
    })
    val grader = ProgrammingUnitTestGrader(suite)
    grader.gradeStateSafe(source, forceGrading = false, List(client)).flatMap { skipped =>
      assertEquals(skipped.gradingStatus, GradingStatus.NotExecuted())
      assertEquals(calls.now(), 0)
      grader.gradeStateSafe(source, forceGrading = true, List(client)).map { graded =>
        assertEquals(graded.gradedState, source)
        assertEquals(graded.gradingStatus, GradingStatus.Correct())
        assertEquals(graded.feedbackInformation, None)
        assertEquals(graded.additionalData, None)
        assertEquals(calls.now(), 1)
      }
    }
  }

  test("student failures differ from invalid tests, service errors, limits and malformed reports") {
    val source = ProgrammingStatePythonString("def add(a, b): return a + b")
    val grader = ProgrammingUnitTestGrader(SampleBeTest("assert add(1, 2) == 3"))
    val rows = List(
      (ujson.Obj("status" -> "passed", "reason" -> ""), false, true),
      (ujson.Obj("status" -> "failed", "reason" -> "assertion"), false, false),
      (ujson.Obj("status" -> "failed", "reason" -> "student-syntax"), false, false),
      (ujson.Obj("status" -> "failed", "reason" -> "student-runtime"), false, false),
      (ujson.Obj("status" -> "error", "reason" -> "invalid-tests"), true, false),
      (ujson.Obj("status" -> "error", "reason" -> "test-runtime"), true, false),
      (ujson.Obj("status" -> "error", "reason" -> "execution-limit"), true, false),
      (ujson.Obj("status" -> "error", "reason" -> "java-execution"), true, false),
      (ujson.Obj("status" -> "passed", "reason" -> "", "private" -> "payload"), true, false),
      (ujson.Obj("status" -> "passed", "reason" -> "private payload"), true, false),
      (ujson.Obj("status" -> "unknown", "reason" -> ""), true, false),
      (ujson.Null, true, false))
    Future.traverse(rows) { (report, isError, passed) =>
      grader.gradeStateSafe(source, forceGrading = true, List(pythonClient(report))).map { graded =>
        assertEquals(graded.gradedState, source)
        assertEquals(graded.gradingStatus.isInstanceOf[GradingStatus.TestError], isError)
        if !isError then assertEquals(graded.gradingStatus, if passed then GradingStatus.Correct() else GradingStatus.Incorrect())
        assert(!write(graded).contains("private payload"))
      }
    }
  }

  test("missing clients, unsupported Java returns and oversized inputs cannot produce an incorrect grade") {
    val suite = SampleBeTest("assert True")
    val grader = ProgrammingUnitTestGrader(suite)
    val calls = State(0)
    val client = pythonClient(ujson.Obj("status" -> "passed", "reason" -> ""), _ => calls.update(_ + 1))
    val source = ProgrammingStatePythonString("pass")
    val returningJava = ProgrammingStateJavaString("public class Main { static int answer() { return 3; } public static void main(String[] args) {} }")
    val failClient = WorkerCommands.runPython.toLocalExecutionClient((_, _) => Future.failed(IllegalStateException("private service data")))
    for {
      missing <- grader.gradeStateSafe(source, forceGrading = true, Nil)
      unsupported <- grader.gradeStateSafe(returningJava, forceGrading = true, List(client))
      oversized <- grader.gradeStateSafe(ProgrammingStatePythonString(" " * 32769), forceGrading = true, List(client))
      empty <- ProgrammingUnitTestGrader(SampleBeTest(" \n")).gradeStateSafe(source, forceGrading = true, List(client))
      service <- grader.gradeStateSafe(source, forceGrading = true, List(failClient))
    } yield {
      List(missing, unsupported, oversized, empty, service).foreach { result =>
        assert(result.gradingStatus.isInstanceOf[GradingStatus.TestError])
        assertEquals(result.additionalData, None)
        assert(!write(result).contains("private service data"))
      }
      assertEquals(calls.now(), 0)
    }
  }

  test("a passed report cannot carry unexpected stdout stderr or callback operations") {
    val source = ProgrammingStatePythonString("pass")
    val grader = ProgrammingUnitTestGrader(SampleBeTest("assert True"))
    val base = WorkerCommands.RunPythonResult(ujson.Obj("status" -> "passed", "reason" -> ""), "", "", Nil)
    val malformed = List(base.copy(stdout = "private output"), base.copy(stderr = "private error"),
      base.copy(callbackOps = List(WorkerCommands.PythonCallback("private module", "private method", Nil))))
    Future.traverse(malformed) { report =>
      val client = WorkerCommands.runPython.toLocalExecutionClient((_, _) => Future.successful(report))
      grader.gradeStateSafe(source, forceGrading = true, List(client)).map { graded =>
        assert(graded.gradingStatus.isInstanceOf[GradingStatus.TestError])
        assert(!write(graded).contains("private"))
      }
    }
  }

  test("Java assertions use original method names and an explicit completed Turtle report") {
    val source = ProgrammingStateJavaString("public class Main { static void draw(int side) { Turtle.forward(side); } public static void main(String[] args) { draw(3); } }")
    val suite = SampleBeTest("assert run_java('draw', (3,))['commands'] == [['forward', 3]]")
    val client = pythonClient(ujson.Obj("status" -> "passed", "reason" -> ""), request => {
      assertEquals(request.context("_eva_tests"), ujson.Str(suite.pythonTestCode))
      assertEquals(request.context("_eva_is_java"), ujson.Bool(true))
      assertEquals(request.context("_eva_java_methods").obj.keySet, Set("draw"))
      assert(request.context("_eva_source").str.startsWith("def java_turtle_run("))
      assert(request.code.contains("invocation.get(\"status\") != \"Completed\""))
      assert(request.code.contains("namespace = {\"run_java\": run_java}"))
    })
    val states = List[ProgrammingState](source, source.toBeExpressionState, source.toPython, source.toSnapXml)
    Future.traverse(states) { state =>
      val saved = write[ProgrammingState](state)
      ProgrammingUnitTestGrader(suite).gradeStateSafe(state, forceGrading = true, List(client)).map { graded =>
        assertEquals(graded.gradedState, state)
        assertEquals(graded.gradingStatus, GradingStatus.Correct())
        assertEquals(write[ProgrammingState](state), saved)
      }
    }
  }

  test("invalid Java editing metadata and ordinary Snap projects do not run as student Python") {
    val calls = State(0)
    val client = pythonClient(ujson.Obj("status" -> "passed", "reason" -> ""), _ => calls.update(_ + 1))
    val grader = ProgrammingUnitTestGrader(SampleBeTest("assert run_java()['status'] == 'Completed'"))
    val states = List[ProgrammingState](ProgrammingStatePythonString("import java_turtle\n"),
      ProgrammingState.ProgrammingStateSnapXml("<project><notes>EduSquirrel Java Turtle 1\nnot json</notes></project>"),
      ProgrammingStateSnapXmlHelper.mini)
    Future.traverse(states) { state =>
      val saved = write[ProgrammingState](state)
      grader.gradeStateSafe(state, forceGrading = true, List(client)).map { graded =>
        assert(graded.gradingStatus.isInstanceOf[GradingStatus.TestError])
        assertEquals(graded.gradedState, state)
        assertEquals(write[ProgrammingState](state), saved)
      }
    }.map(_ => assertEquals(calls.now(), 0))
  }

  test("unit-test graders and status-only grading results roundtrip through the central codecs") {
    val suite = SampleBeTest("assert True\r\n")
    val grader: WorkbookInteractionGrader[ProgrammingState, Any] = ProgrammingUnitTestGrader(suite, GradingImportance.HIDDEN_TEST)
    assertEquals(read[WorkbookInteractionGrader[ProgrammingState, Any]](write(grader)), grader)
    assertEquals(readBinary[WorkbookInteractionGrader[ProgrammingState, Any]](writeBinary(grader)), grader)
    val state: ProgrammingState = ProgrammingStatePythonString("pass\r\n")
    val result = GradingResult[ProgrammingState, Any](grader, state, GradingStatus.Correct())
    assert(writeJs(result).obj.contains("gradingTime"))
    assertEquals(read[GradingResult[ProgrammingState, Any]](write(result)), result)
    assertEquals(readBinary[GradingResult[ProgrammingState, Any]](writeBinary(result)), result)
    List(BeTestResult.Passed, BeTestResult.Failed, BeTestResult.Error("service unavailable")).foreach { value =>
      assertEquals(read[BeTestResult](write(value)), value)
      assertEquals(readBinary[BeTestResult](writeBinary(value)), value)
      assertEquals(value.hasPassed, value == BeTestResult.Passed)
    }
  }
}
