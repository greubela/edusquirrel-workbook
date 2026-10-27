package it.evadid.workbook.elements.interactionElements.programming
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*

import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
import it.evadid.vm.test.{BeTestResult, BeTestSuite, ProgrammingTestSuite, SampleBeTest}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingExerciseFullJava}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import munit.FunSuite
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.elements.interactionElements.slideshow.SlideshowGrader
import it.evadid.workbook.abstractions.scaffolding.ProgrammingHintScaffolder
import it.evadid.distribution.command.SerializedException
import upickle.default.*
import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.workbook.abstractions.grading.*

import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDateTime

class ProgrammingExerciseFactorySpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  private def assertionGrader(suite: SampleBeTest,
      importance: GradingImportance = GradingImportance.REQUIRED_TEST): ProgrammingUnitTestGrader =
    ProgrammingUnitTestGrader(ProgrammingUnitTestGrader.LegacyTestId, suite.pythonTestCode, importance)

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
    assertEquals(restored.serializerInteractionContent.deserialize(saved).fingerprint(),
      savedDraft.fingerprint())
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
    assertEquals(restored.fingerprint(), invalidDraft.fingerprint())
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
    val expected = List(assertionGrader(suite))
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
    val grader = assertionGrader(suite)
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
    val grader = assertionGrader(SampleBeTest("assert add(1, 2) == 3"))
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
    val grader = assertionGrader(suite)
    val calls = State(0)
    val client = pythonClient(ujson.Obj("status" -> "passed", "reason" -> ""), _ => calls.update(_ + 1))
    val source = ProgrammingStatePythonString("pass")
    val returningJava = ProgrammingStateJavaString("public class Main { static int answer() { return 3; } public static void main(String[] args) {} }")
    val failClient = WorkerCommands.runPython.toLocalExecutionClient((_, _) => Future.failed(IllegalStateException("private service data")))
    for {
      missing <- grader.gradeStateSafe(source, forceGrading = true, Nil)
      unsupported <- grader.gradeStateSafe(returningJava, forceGrading = true, List(client))
      oversized <- grader.gradeStateSafe(ProgrammingStatePythonString(" " * 32769), forceGrading = true, List(client))
      empty <- assertionGrader(SampleBeTest(" \n")).gradeStateSafe(source, forceGrading = true, List(client))
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
    val grader = assertionGrader(SampleBeTest("assert True"))
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
    val states = List[ProgrammingState](source, source.toBeExpression, source.toPython, source.toSnapXml)
    Future.traverse(states) { state =>
      val saved = write[ProgrammingState](state)
      assertionGrader(suite).gradeStateSafe(state, forceGrading = true, List(client)).map { graded =>
        assertEquals(graded.gradedState, state)
        assertEquals(graded.gradingStatus, GradingStatus.Correct())
        assertEquals(write[ProgrammingState](state), saved)
      }
    }
  }

  test("invalid Java editing metadata and ordinary Snap projects do not run as student Python") {
    val calls = State(0)
    val client = pythonClient(ujson.Obj("status" -> "passed", "reason" -> ""), _ => calls.update(_ + 1))
    val grader = assertionGrader(SampleBeTest("assert run_java()['status'] == 'Completed'"))
    val states = List[ProgrammingState](ProgrammingStatePythonString("import java_turtle\n"),
      ProgrammingStateSnapXml("<project><notes>EduSquirrel Java Turtle 1\nnot json</notes></project>"),
      ProgrammingStateSnapXml.mini)
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
    val grader: WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo] = assertionGrader(suite, GradingImportance.HIDDEN_TEST)
    assertEquals(read[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]](write(grader)), grader)
    assertEquals(readBinary[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]](writeBinary(grader)), grader)
    val state: ProgrammingState = ProgrammingStatePythonString("pass\r\n")
    val result = GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](grader, state, GradingStatus.Correct())
    assert(writeJs(result).obj.contains("gradingTime"))
    assertEquals(read[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](write(result)), result)
    assertEquals(readBinary[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](writeBinary(result)), result)
    List(BeTestResult.Passed, BeTestResult.Failed, BeTestResult.Error("service unavailable")).foreach { value =>
      assertEquals(read[BeTestResult](write(value)), value)
      assertEquals(readBinary[BeTestResult](writeBinary(value)), value)
      assertEquals(value.hasPassed, value == BeTestResult.Passed)
    }
  }

  test("programming histories preserve typed test descriptions in both languages") {
    val info = AdditionalProgrammingTestInfo(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      German -> "Der Aufruf draw(3) zeichnet eine Linie. Grüße 🐢",
      English -> "The call draw(3) draws one line."
    )))
    val suite = Some(SampleBeTest("assert True"))
    val exercises: List[WorkbookInteractionElementFull[ProgrammingState, AdditionalProgrammingTestInfo, Any]] =
      List(ProgrammingExercise("typed-programming-history", suite), ProgrammingExerciseFullJava("typed-java-history", suite))
    exercises.foreach { exercise =>
      val variable = exercise.interactionVariable
      val state = variable.currentValue
      val result = GradingResult(exercise.gradingElements.head, state, GradingStatus.Correct(), additionalData = Some(info))
      variable.updateHistory(history => history.copy(gradings = Map(state -> GradedState(state, Set(result)))))
      val original = variable.history
      val restored = List(variable.deserializeHistory(variable.serializeHistory(original)),
        readBinary[InteractionVariableHistory[ProgrammingState, AdditionalProgrammingTestInfo, Any]](writeBinary(original)))
      restored.foreach { history =>
        assertEquals(history, original)
        val saved = history.gradings(state).testsWithResults.head.additionalData.get
        assertEquals(saved.humanReadableTestExecutionDescription.getInLanguage(German),
          info.humanReadableTestExecutionDescription.getInLanguage(German))
        assertEquals(saved.humanReadableTestExecutionDescription.getInLanguage(English),
          info.humanReadableTestExecutionDescription.getInLanguage(English))
      }
    }
  }

  test("existing status-only results and histories retain their empty additional data") {
    val exercise = ProgrammingExerciseFullJava("status-history", Some(SampleBeTest("assert True")))
    val variable = exercise.interactionVariable
    val state = variable.currentValue
    val result = GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](
      exercise.gradingElements.head, state, GradingStatus.Incorrect())
    val previousGrader = read[WorkbookInteractionGrader[ProgrammingState, Any]](write(exercise.gradingElements.head))
    val previousResult = GradingResult[ProgrammingState, Any](previousGrader, state,
      GradingStatus.Incorrect(), gradingTime = result.gradingTime)
    assertEquals(read[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](write(previousResult)), result)
    assertEquals(readBinary[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](writeBinary(previousResult)), result)
    val encoded = writeJs(result).obj.toMap
    List(encoded - "additionalData", encoded.updated("additionalData", writeJs[Option[Any]](None))).foreach { fields =>
      val restored = read[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](ujson.Obj.from(fields))
      assertEquals(restored, result)
    }
    variable.updateHistory(history => history.copy(gradings = Map(state -> GradedState(state, Set(result)))))
    val original = variable.history
    assertEquals(variable.deserializeHistory(variable.serializeHistory(original)), original)
    assertEquals(readBinary[InteractionVariableHistory[ProgrammingState, AdditionalProgrammingTestInfo, Any]](
      writeBinary(original)), original)
    val previousHistory = InteractionVariableHistory[ProgrammingState, Any, Any](original.events,
      Map(state -> GradedState(state, Set(previousResult))), Map.empty)
    assertEquals(variable.deserializeHistory(write(previousHistory)), original)
    assertEquals(readBinary[InteractionVariableHistory[ProgrammingState, AdditionalProgrammingTestInfo, Any]](
      writeBinary(previousHistory)), original)
    val unknownData = writeJs(previousResult.copy(additionalData = Some("old untyped payload")))
    intercept[Exception](read[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](unknownData))
  }

  test("a legacy assertion block retains its exact suite JSON and creates one grader") {
    val json = """{"$type":"SampleBeTest","pythonTestCode":"assert True\r\nassert False\n"}"""
    val suite = read[BeTestSuite](json)
    val expected = assertionGrader(SampleBeTest("assert True\r\nassert False\n"))
    assertEquals(write[BeTestSuite](suite), json)
    assertEquals(readBinary[BeTestSuite](writeBinary(suite)), suite)
    assertEquals(suite.graders, List(expected))
    assertEquals(ProgrammingExercise("old-block", Some(suite)).gradingElements, List(expected))
    assertEquals(ProgrammingExerciseFullJava("old-java-block", Some(suite)).gradingElements, List(expected))
  }

  test("five explicit programming graders retain their identities in suites and workbook definitions") {
    val graders: List[ProgrammingTestGrader] = (1 to 5).map { index =>
      ProgrammingUnitTestGrader(s"check-$index", s"assert $index == $index\r\n",
        if index == 5 then GradingImportance.HIDDEN_TEST else GradingImportance.REQUIRED_TEST)
    }.toList
    val suite: BeTestSuite = ProgrammingTestSuite(graders)
    assertEquals(read[BeTestSuite](write(suite)), suite)
    assertEquals(readBinary[BeTestSuite](writeBinary(suite)), suite)
    graders.foreach { grader =>
      assertEquals(read[ProgrammingTestGrader](write(grader)), grader)
      assertEquals(readBinary[ProgrammingTestGrader](writeBinary(grader)), grader)
      val registered: WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo] = grader
      assertEquals(read[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]](write(registered)), registered)
      assertEquals(readBinary[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]](writeBinary(registered)), registered)
    }
    val exercises: List[WorkbookInteractionElementFull[ProgrammingState, AdditionalProgrammingTestInfo, Any]] = List(
      ProgrammingExercise("five-general", Some(suite)), ProgrammingExerciseFullJava("five-java", Some(suite)))
    exercises.foreach { exercise =>
      assertEquals(exercise.gradingElements, graders)
      assertEquals(exercise.gradingElements.map(_.runningBehavior).toSet, Set(GraderRunningBehavior.RUN_ONLY_MANUALLY))
      List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike).foreach { codec =>
        val restored = codec.deserialize(codec.serialize(exercise))
          .asInstanceOf[WorkbookInteractionElementFull[ProgrammingState, AdditionalProgrammingTestInfo, Any]]
        assertEquals(restored.getClass, exercise.getClass)
        assertEquals(restored.elementId, exercise.elementId)
        assertEquals(restored.gradingElements, graders)
        assertEquals(restored.toSerialized.getElementAs[Option[BeTestSuite]]("testSuite"), Some(suite))
      }
    }
  }

  test("central grading executes each explicit test once and publishes independent failures") {
    val graders = List("passed", "failed", "invalid", "unavailable", "last").map { id =>
      ProgrammingUnitTestGrader(id, s"assert True # $id")
    }
    val byCode = graders.map(grader => grader.pythonTestCode -> grader.testId).toMap
    val calls = State(Map.empty[String, Int])
    val client = WorkerCommands.runPython.toLocalExecutionClient((request, _) => {
      val id = byCode(request.context("_eva_tests").str)
      calls.synchronized { calls.update(current => current.updated(id, current.getOrElse(id, 0) + 1)) }
      val response = id match {
        case "failed" => Future.successful(ujson.Obj("status" -> "failed", "reason" -> "assertion"))
        case "invalid" => Future.successful(ujson.Obj("status" -> "error", "reason" -> "invalid-tests"))
        case "unavailable" => Future.failed(IllegalStateException("private transport detail"))
        case _ => Future.successful(ujson.Obj("status" -> "passed", "reason" -> ""))
      }
      response.map(value => WorkerCommands.RunPythonResult(value, "", "", Nil))
    })
    val exercise = ProgrammingExercise("independent-tests", Some(ProgrammingTestSuite(graders)))
    val variable = exercise.interactionVariable
    val state: ProgrammingState = ProgrammingStatePythonString("pass")
    variable.updateHistory(_.copy(events = Set(InteractionVariableState(state, UpdateImportance.MAJOR, LocalDateTime.now()))))
    variable.startGrading(null, List(client), forceGrading = true).map { _ =>
      assertEquals(calls.now(), graders.map(_.testId -> 1).toMap)
      val results = variable.history.gradings(state).testsWithResults
      assertEquals(results.size, 5)
      assert(results.forall(_.gradedState == state))
      val statuses = results.map(result => result.grader.asInstanceOf[ProgrammingTestGrader].testId -> result.gradingStatus).toMap
      assertEquals(statuses("passed"), GradingStatus.Correct())
      assertEquals(statuses("last"), GradingStatus.Correct())
      assertEquals(statuses("failed"), GradingStatus.Incorrect())
      assert(statuses("invalid").isInstanceOf[GradingStatus.TestError])
      assert(statuses("unavailable").isInstanceOf[GradingStatus.TestError])
      assert(!write(variable.history).contains("private transport detail"))
    }
  }

  test("equal assertion bodies with different IDs remain separate in saved grading histories") {
    val first = ProgrammingUnitTestGrader("first", "assert True\r\n")
    val second = ProgrammingUnitTestGrader("second", first.pythonTestCode)
    val suite = ProgrammingTestSuite(List(first, second))
    val exercise = ProgrammingExerciseFullJava("separate-identities", Some(suite))
    val variable = exercise.interactionVariable
    val state = variable.currentValue
    val time = LocalDateTime.parse("2026-10-26T12:00")
    val results = Set(
      GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](first, state, GradingStatus.Correct(), gradingTime = time),
      GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](second, state, GradingStatus.Correct(), gradingTime = time))
    assertEquals(results.size, 2)
    variable.updateHistory(history => history.copy(gradings = Map(state -> GradedState(state, results))))
    val history = variable.history
    List(variable.deserializeHistory(variable.serializeHistory(history)),
      readBinary[InteractionVariableHistory[ProgrammingState, AdditionalProgrammingTestInfo, Any]](writeBinary(history))).foreach { restored =>
      assertEquals(restored, history)
      assertEquals(restored.gradings(state).testsWithResults.map(_.grader), Set[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]](first, second))
    }
  }

  test("programming collections reject missing or repeated test identities") {
    List[String]("", " ", " leading", "trailing ", null).foreach { id =>
      intercept[IllegalArgumentException](ProgrammingUnitTestGrader(id, "assert True"))
      val json = ujson.Obj("testId" -> writeJs(id), "pythonTestCode" -> "assert True")
      intercept[Exception](read[ProgrammingUnitTestGrader](json))
    }
    val first = ProgrammingUnitTestGrader("same", "assert True")
    val second = ProgrammingUnitTestGrader("same", "assert False")
    List(List(first, first), List(first, second)).foreach { graders =>
      intercept[IllegalArgumentException](ProgrammingTestSuite(graders))
      val json = ujson.Obj("$type" -> "ProgrammingTestSuite",
        "graders" -> ujson.Arr.from(graders.map(value => writeJs[ProgrammingTestGrader](value))))
      intercept[Exception](read[BeTestSuite](json))
      intercept[Exception](readBinary[BeTestSuite](writeBinary(json)))
    }
    intercept[IllegalArgumentException](ProgrammingTestSuite(null))
    intercept[IllegalArgumentException](ProgrammingTestSuite(List(null)))
  }

  test("legacy grader fixtures read through direct and registered codecs and write canonical fields") {
    val legacySuite = ujson.read("""{"$type":"SampleBeTest","pythonTestCode":"assert True\r\nassert False\n"}""")
    val fixtures = List(
      ("""{"testSuite":{"$type":"SampleBeTest","pythonTestCode":"assert True\r\nassert False\n"},"serializedType":"ProgrammingUnitTestGrader"}""", GradingImportance.REQUIRED_TEST),
      ("""{"testSuite":{"$type":"SampleBeTest","pythonTestCode":"assert True\r\nassert False\n"},"importance":"HIDDEN_TEST","serializedType":"ProgrammingUnitTestGrader"}""", GradingImportance.HIDDEN_TEST))
    val stateJson = ujson.read("""{"$type":"PythonSource","value":{"$type":"ProgrammingStatePythonString","code":"pass\r\n"}}""")
    val state: ProgrammingState = ProgrammingStatePythonString("pass\r\n")
    val time = LocalDateTime.parse("2026-10-26T12:00")
    fixtures.foreach { (json, importance) =>
      val expected = assertionGrader(SampleBeTest("assert True\r\nassert False\n"), importance)
      val direct = ujson.Obj.from(ujson.read(json).obj.toMap - "serializedType")
      assertEquals(read[ProgrammingUnitTestGrader](direct), expected)
      assertEquals(readBinary[ProgrammingUnitTestGrader](writeBinary(direct)), expected)
      assertEquals(read[ProgrammingTestGrader](ujson.Str(json)), expected)
      assertEquals(readBinary[ProgrammingTestGrader](writeBinary[ujson.Value](ujson.Str(json))), expected)
      assertEquals(read[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]](ujson.Str(json)), expected)
      val canonical = writeJs(expected)
      assertEquals(canonical.obj.keySet.toSet,
        if importance == GradingImportance.REQUIRED_TEST then Set("testId", "pythonTestCode")
        else Set("testId", "pythonTestCode", "importance"))
      assertEquals(canonical("testId"), ujson.Str(ProgrammingUnitTestGrader.LegacyTestId))
      assertEquals(canonical("pythonTestCode"), legacySuite("pythonTestCode"))
      assertEquals(read[ProgrammingUnitTestGrader](canonical), expected)
      val resultJson = ujson.Obj("grader" -> ujson.Str(json), "gradedState" -> stateJson,
        "gradingStatus" -> ujson.Obj("$type" -> "Incorrect"), "gradingTime" -> "2026-10-26T12:00")
      val result = GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](expected, state,
        GradingStatus.Incorrect(), gradingTime = time)
      assertEquals(read[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](resultJson), result)
      assertEquals(readBinary[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](writeBinary(resultJson)), result)
      val historyJson = ujson.Obj("events" -> ujson.Arr(ujson.Obj("value" -> stateJson,
        "updateImportance" -> "MAJOR", "timestamp" -> "2026-10-26T12:00")),
        "gradings" -> ujson.Arr(ujson.Arr(stateJson, ujson.Obj("state" -> stateJson,
          "testsWithResults" -> ujson.Arr(resultJson)))), "scaffoldings" -> ujson.Arr())
      val history = read[InteractionVariableHistory[ProgrammingState, AdditionalProgrammingTestInfo, Any]](historyJson)
      assertEquals(history.gradings(state).testsWithResults, Set(result))
      assertEquals(readBinary[InteractionVariableHistory[ProgrammingState, AdditionalProgrammingTestInfo, Any]](writeBinary(historyJson)), history)
    }
  }

  test("canonical grader readers reject ambiguous and unknown legacy suite payloads") {
    val suite = ujson.Obj("$type" -> "SampleBeTest", "pythonTestCode" -> "assert True")
    val malformed = List(
      ujson.Obj("testSuite" -> suite, "testId" -> "new"),
      ujson.Obj("testSuite" -> suite, "pythonTestCode" -> "assert False"),
      ujson.Obj("testSuite" -> ujson.Obj("$type" -> "UnknownSuite", "pythonTestCode" -> "assert True")),
      ujson.Obj("testSuite" -> ujson.Obj("$type" -> "ProgrammingTestSuite", "graders" -> ujson.Arr())),
      ujson.Obj("testSuite" -> ujson.Obj("$type" -> "SampleBeTest", "pythonTestCode" -> "assert True", "extra" -> 1)),
      ujson.Obj("testSuite" -> ujson.Null),
      ujson.Obj("testId" -> "new", "pythonTestCode" -> "assert True", "unexpected" -> true),
      ujson.Obj("testId" -> "new", "pythonTestCode" -> "assert True", "serializedType" -> "SlideshowGrader"),
      ujson.Obj("pythonTestCode" -> "assert True"),
      ujson.Obj("testId" -> "new"))
    malformed.foreach { value =>
      intercept[Exception](read[ProgrammingUnitTestGrader](value))
      intercept[Exception](readBinary[ProgrammingUnitTestGrader](writeBinary(value)))
    }
  }

  test("programming suite codecs reject registered graders from other interaction types") {
    val foreign = WorkbookInteractionGrader.universalSerializer.serialize(SlideshowGrader(true))
    val encoded = ujson.Str(foreign)
    intercept[Exception](read[ProgrammingTestGrader](encoded))
    intercept[Exception](readBinary[ProgrammingTestGrader](writeBinary[ujson.Value](encoded)))
    val suite = ujson.Obj("$type" -> "ProgrammingTestSuite", "graders" -> ujson.Arr(encoded))
    intercept[Exception](read[BeTestSuite](suite))
    intercept[Exception](readBinary[BeTestSuite](writeBinary(suite)))
    val definition = ProgrammingExerciseFullJava("foreign-test").toSerialized
      .withElementAddedAs[Option[ujson.Value]]("testSuite", Some(suite))
    intercept[SerializedException](ProgrammingExerciseFullJava.factory.fromSerializedElement(definition, Map.empty))
  }

  test("programming collections preserve assertion and shape graders together") {
    val assertion = ProgrammingUnitTestGrader("sum", "assert add(1, 2) == 3")
    val drawing = RecreateShapeGrader(TurtleGraphic[Double](Seq.empty), isRequired = true, testId = "drawing")
    val suite: BeTestSuite = ProgrammingTestSuite(List(assertion, drawing))
    List(read[BeTestSuite](write(suite)), readBinary[BeTestSuite](writeBinary(suite))).foreach { restored =>
      assertEquals(restored, suite)
      assertEquals(restored.graders.map(_.testId), List("sum", "drawing"))
    }
    val exercise = ProgrammingExerciseFullJava("mixed-graders", Some(suite))
    assertEquals(exercise.gradingElements, List(assertion, drawing))
    val restored = WorkbookElementFactory.serializerRefBasedJson
      .deserialize(WorkbookElementFactory.serializerRefBasedJson.serialize(exercise))
      .asInstanceOf[ProgrammingExerciseFullJava]
    assertEquals(restored.gradingElements, exercise.gradingElements)
  }
}
