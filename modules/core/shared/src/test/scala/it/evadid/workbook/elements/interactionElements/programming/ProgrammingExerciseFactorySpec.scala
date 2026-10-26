package it.evadid.workbook.elements.interactionElements.programming
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStateJavaString
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*

import it.evadid.vm.test.{BeTestResult, BeTestSuite, ProgrammingTestSuite, SampleBeTest}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingExerciseFullJava}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import munit.FunSuite
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.elements.interactionElements.slideshow.SlideshowGrader
import it.evadid.workbook.abstractions.scaffolding.ProgrammingHintScaffolder
import it.evadid.distribution.command.SerializedException
import upickle.default.*
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStatePythonString

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

  private def nativeSnap(scripts: String): ProgrammingState.ProgrammingStateSnapXml =
    ProgrammingState.ProgrammingStateSnapXml(s"""<project name="native" version="2"><notes>Grüße 🐢</notes>
      |<stage name="Stage"><variables/><blocks/><scripts/><sprites><sprite name="Turtle">
      |<variables/><blocks/><scripts>$scripts</scripts></sprite></sprites></stage></project>""".stripMargin)

  private def observationPayload(count: Int, commands: Seq[ujson.Value]): ujson.Value =
    ujson.Obj("commandCount" -> count, "commands" -> ujson.Arr.from(commands))

  private def observedReport(observation: ujson.Value, status: String = "passed", reason: String = ""): ujson.Value =
    ujson.Obj("status" -> status, "reason" -> reason, "programObservation" -> observation)

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
      assertEquals(request.context("_eva_entry_point"), ujson.Null)
      assertEquals(request.context("_eva_observe_program"), ujson.Bool(false))
      assert(!request.context.contains("_eva_is_java") && !request.context.contains("_eva_java_methods"))
      val primitives = request.context("_eva_turtle_primitives")
      assertEquals(primitives("forward"), ujson.Obj("command" -> "forward", "arity" -> 1, "inputs" -> ujson.Arr("Numeric")))
      assertEquals(primitives("turn_right"), ujson.Obj("command" -> "right", "arity" -> 1, "inputs" -> ujson.Arr("Numeric")))
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
      (ujson.Obj("status" -> "error", "reason" -> "program-execution"), true, false),
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

  test("public test descriptions resolve once without rerunning a completed grade") {
    val source = ProgrammingStatePythonString("def add(a, b): return a + b")
    val descriptionId = LanguageMapContentId("tests/add-public-description")
    val descriptions = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      German -> "Der Aufruf verwendet zwei positive Zahlen. Grüße 🐢",
      English -> "The call uses two positive numbers."
    ))
    val cases = for {
      importance <- List(GradingImportance.REQUIRED_TEST, GradingImportance.OPTIONAL_TEST)
      passed <- List(true, false)
    } yield (importance, passed)
    Future.traverse(cases) { (importance, passed) =>
      val calls = State(0)
      val lookups = State(0)
      val resolver = new LanguageMapIdResolver(State[HumanLanguage](German).observable) {
        override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] = {
          assertEquals(id, descriptionId)
          lookups.update(_ + 1)
          Future.successful(descriptions)
        }
        override def languageFuture(): Future[HumanLanguage] =
          Future.failed(IllegalStateException("Descriptions must retain every available language."))
      }
      val report = if passed then ujson.Obj("status" -> "passed", "reason" -> "")
        else ujson.Obj("status" -> "failed", "reason" -> "assertion")
      val client = pythonClient(report, _ => calls.update(_ + 1))
      val grader = ProgrammingUnitTestGrader("addition", "assert add(1, 2) == 3", importance, Some(descriptionId))
      grader.gradeStateSafe(source, forceGrading = true, List(client), resolver).map { result =>
        assertEquals(calls.now(), 1)
        assertEquals(lookups.now(), 1)
        assertEquals(result.gradedState, source)
        assertEquals(result.grader, grader)
        assertEquals(result.gradingStatus, if passed then GradingStatus.Correct() else GradingStatus.Incorrect())
        assertEquals(result.feedbackInformation, None)
        assertEquals(result.additionalData, Some(AdditionalProgrammingTestInfo(descriptions)))
        assertEquals(read[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](write(result)), result)
        assertEquals(readBinary[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](writeBinary(result)), result)
      }
    }.map(_ => ())
  }

  test("hidden unexecuted and unavailable test descriptions are never resolved") {
    val source = ProgrammingStatePythonString("pass")
    val configured = ProgrammingUnitTestGrader("public", "assert True",
      publicDescription = Some(LanguageMapContentId("tests/public-description")))
    val cases = List(
      (configured.copy(publicDescription = None), true, true, false),
      (configured.copy(importance = GradingImportance.HIDDEN_TEST), true, true, false),
      (configured, false, true, false),
      (configured, true, false, false),
      (configured, true, true, true)
    )
    Future.traverse(cases) { (grader, force, useResolver, error) =>
      val calls = State(0)
      val lookups = State(0)
      val resolver = new LanguageMapIdResolver(State[HumanLanguage](English).observable) {
        override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] = {
          lookups.update(_ + 1)
          Future.failed(IllegalStateException("This description must not be resolved."))
        }
      }
      val report = if error then ujson.Obj("status" -> "error", "reason" -> "invalid-tests")
        else ujson.Obj("status" -> "passed", "reason" -> "")
      val client = pythonClient(report, _ => calls.update(_ + 1))
      val evaluated = if useResolver then grader.gradeStateSafe(source, force, List(client), resolver)
        else grader.gradeStateSafe(source, force, List(client))
      evaluated.map { result =>
        assertEquals(calls.now(), if force then 1 else 0)
        assertEquals(lookups.now(), 0)
        assertEquals(result.additionalData, None)
        if error then assert(result.gradingStatus.isInstanceOf[GradingStatus.TestError])
        else assertEquals(result.gradingStatus, if force then GradingStatus.Correct() else GradingStatus.NotExecuted())
      }
    }.map(_ => ())
  }

  test("description bounds and resolver failures cannot change a test outcome") {
    val source = ProgrammingStatePythonString("pass")
    val grader = ProgrammingUnitTestGrader("bounded", "assert False",
      publicDescription = Some(LanguageMapContentId("tests/bounded-description")))
    def translated(values: Map[HumanLanguage, String]): Future[LanguageMap[HumanLanguage]] =
      Future.successful(LanguageMap.mapBasedLanguageMap(values))
    val boundary = "ä" * 1024
    val cases: List[(() => Future[LanguageMap[HumanLanguage]], Map[HumanLanguage, String])] = List(
      (() => translated(Map.empty), Map.empty),
      (() => translated(Map(German -> " \r\n", English -> ("x" * 1025))), Map.empty),
      (() => translated(Map(German -> null, English -> "A public description.")), Map(English -> "A public description.")),
      (() => translated(Map(German -> boundary, English -> ("x" * 1025))), Map(German -> boundary)),
      (() => translated(Map(German -> ("x" * 1025), English -> "English remains available.")),
        Map(English -> "English remains available.")),
      (() => Future.failed(IllegalStateException("private resolver detail")), Map.empty),
      (() => throw IllegalStateException("private synchronous resolver detail"), Map.empty),
      (() => null, Map.empty),
      (() => Future.successful(null), Map.empty)
    )
    Future.traverse(cases) { (resolve, expected) =>
      val calls = State(0)
      val lookups = State(0)
      val resolver = new LanguageMapIdResolver(State[HumanLanguage](German).observable) {
        override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] = {
          lookups.update(_ + 1)
          resolve()
        }
      }
      val client = pythonClient(ujson.Obj("status" -> "failed", "reason" -> "assertion"), _ => calls.update(_ + 1))
      grader.gradeStateSafe(source, forceGrading = true, List(client), resolver).map { result =>
        assertEquals(result.gradingStatus, GradingStatus.Incorrect())
        assertEquals(result.gradedState, source)
        assertEquals(calls.now(), 1)
        assertEquals(lookups.now(), 1)
        assertEquals(result.additionalData.map(_.humanReadableTestExecutionDescription.availableLanguages),
          Option.when(expected.nonEmpty)(expected.keySet))
        result.additionalData.foreach { info =>
          expected.foreach { (language, text) =>
            assertEquals(info.humanReadableTestExecutionDescription.tryGetInLanguage(language), Some(text))
          }
        }
        assert(!write(result).contains("private resolver detail"))
        assert(!write(result).contains("private synchronous resolver detail"))
      }
    }.map(_ => ())
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

  test("only explicitly configured public graders request program observations") {
    val source = ProgrammingStatePythonString("forward(2)\n")
    val default = ProgrammingUnitTestGrader("default", "assert run_program()")
    val enabled = default.copy(testId = "enabled", publishProgramObservation = true)
    val hidden = enabled.copy(testId = "hidden", importance = GradingImportance.HIDDEN_TEST)
    val oldReport = ujson.Obj("status" -> "passed", "reason" -> "")
    Future.traverse(List(default -> false, enabled -> true, hidden -> false)) { (grader, expected) =>
      val calls = State(0)
      val client = pythonClient(oldReport, request => {
        calls.update(_ + 1)
        assertEquals(request.context("_eva_observe_program"), ujson.Bool(expected))
      })
      grader.gradeStateSafe(source, forceGrading = true, List(client)).map { result =>
        assertEquals(result.gradingStatus, GradingStatus.Correct())
        assertEquals(result.additionalData, None)
        assertEquals(calls.now(), 1)
      }
    }.map(_ => ())
  }

  test("observation decoding preserves exact bounded prefixes for successful and failed assertions") {
    val source = ProgrammingStatePythonString("forward(2)\n")
    val grader = ProgrammingUnitTestGrader("observed", "assert run_program()", publishProgramObservation = true)
    val rows = for {
      count <- List(0, 1, 32, 33, 10000)
      (status, reason, expectedStatus) <- List(("passed", "", GradingStatus.Correct()),
        ("failed", "assertion", GradingStatus.Incorrect()))
    } yield (count, status, reason, expectedStatus)
    Future.traverse(rows) { (count, status, reason, expectedStatus) =>
      val prefix = (1 to math.min(32, count)).map(value => ujson.Arr("forward", value))
      val client = pythonClient(observedReport(observationPayload(count, prefix), status, reason))
      grader.gradeStateSafe(source, forceGrading = true, List(client)).map { result =>
        assertEquals(result.gradedState, source)
        assertEquals(result.gradingStatus, expectedStatus)
        val info = result.additionalData.get
        val observation = info.programObservation.get
        assertEquals(observation.commandCount, count)
        assertEquals(observation.omittedCommands, count - math.min(32, count))
        assertEquals(observation.commands, (1 to math.min(32, count))
          .map(value => TurtleCommand[Double]("forward", List(value.toDouble))).toList)
        assertEquals(info.humanReadableTestExecutionDescription.availableLanguages, Set.empty[HumanLanguage])
      }
    }.flatMap { _ =>
      grader.gradeStateSafe(source, forceGrading = true, List(pythonClient(observedReport(ujson.Null)))).map { result =>
        assertEquals(result.gradingStatus, GradingStatus.Correct())
        assertEquals(result.additionalData, None)
      }
    }
  }

  test("unexpected or malformed observations fail safely instead of changing the learner grade") {
    val source = ProgrammingStatePythonString("forward(2)\n")
    val grader = ProgrammingUnitTestGrader("observed", "assert run_program()", publishProgramObservation = true)
    val valid = observationPayload(1, List(ujson.Arr("forward", 2)))
    val prefix32 = (1 to 32).map(value => ujson.Arr("forward", value))
    val invalid = List(
      observationPayload(-1, Nil), observationPayload(10001, prefix32),
      observationPayload(0, List(ujson.Arr("forward", 2))), observationPayload(1, Nil),
      observationPayload(1, List(ujson.Arr("forward", 2), ujson.Arr("forward", 3))),
      observationPayload(33, prefix32.dropRight(1)), observationPayload(33, prefix32 :+ ujson.Arr("forward", 33)),
      observationPayload(1, List(ujson.Arr("PRIVATE_UNKNOWN_COMMAND", 2))),
      observationPayload(1, List(ujson.Arr("fd", 2))), observationPayload(1, List(ujson.Arr("receive_go"))),
      observationPayload(1, List(ujson.Arr("forward"))), observationPayload(1, List(ujson.Arr("forward", 1, 2))),
      observationPayload(1, List(ujson.Arr("goto", 1))), observationPayload(1, List(ujson.Arr("setColor", "PRIVATE_COLOR"))),
      observationPayload(1, List(ujson.Arr("forward", "PRIVATE_ARGUMENT"))),
      observationPayload(1, List(ujson.Arr("forward", true))), observationPayload(1, List(ujson.Arr("forward", ujson.Null))),
      observationPayload(1, List(ujson.Arr("forward", Double.NaN))),
      observationPayload(1, List(ujson.Arr("forward", Double.PositiveInfinity))),
      observationPayload(1, List(ujson.Arr("forward", 9007199254740992d))),
      ujson.Obj("commandCount" -> 0.5, "commands" -> ujson.Arr()),
      ujson.Obj("commandCount" -> "PRIVATE_COUNT", "commands" -> ujson.Arr()),
      ujson.Obj("commandCount" -> 0),
      ujson.Obj("commandCount" -> 0, "commands" -> ujson.Obj()),
      ujson.Obj("commandCount" -> 0, "commands" -> ujson.Arr(), "extra" -> "PRIVATE_EXTRA"),
      ujson.Str("PRIVATE_OBSERVATION"))
    val rows = invalid.map(observedReport(_)) ++ List(
      observedReport(valid, "failed", "student-syntax"), observedReport(valid, "failed", "student-runtime"),
      observedReport(valid, "error", "execution-limit"), observedReport(valid, "error", "test-runtime"))
    Future.traverse(rows) { report =>
      grader.gradeStateSafe(source, forceGrading = true, List(pythonClient(report))).map { result =>
        assert(result.gradingStatus.isInstanceOf[GradingStatus.TestError])
        assertEquals(result.additionalData, None)
        assert(!write(result).contains("PRIVATE_"))
      }
    }.flatMap { _ =>
      Future.sequence(List(grader.copy(publishProgramObservation = false),
        grader.copy(importance = GradingImportance.HIDDEN_TEST)).flatMap { disabled =>
        List(valid, ujson.Null).map { observation =>
          disabled.gradeStateSafe(source, forceGrading = true, List(pythonClient(observedReport(observation)))).map { result =>
            assert(result.gradingStatus.isInstanceOf[GradingStatus.TestError])
            assertEquals(result.additionalData, None)
          }
        }
      }).map(_ => ())
    }
  }

  test("resolving descriptions preserves an existing observation when text is missing or fails") {
    val source = ProgrammingStatePythonString("forward(2)\n")
    val descriptionId = LanguageMapContentId("tests/observed-description")
    val grader = ProgrammingUnitTestGrader("observed", "assert run_program()",
      publicDescription = Some(descriptionId), publishProgramObservation = true)
    val observation = observationPayload(1, List(ujson.Arr("forward", 2)))
    val rows: List[(() => Future[LanguageMap[HumanLanguage]], Option[String])] = List(
      (() => Future.successful(LanguageMap.universalMap(" Public description. ")), Some("Public description.")),
      (() => Future.successful(LanguageMap.empty[HumanLanguage]), None),
      (() => Future.failed(IllegalStateException("PRIVATE_ASYNC_RESOLVER")), None),
      (() => throw IllegalStateException("PRIVATE_SYNC_RESOLVER"), None))
    Future.traverse(rows) { (resolve, expectedDescription) =>
      val calls = State(0)
      val resolutions = State(0)
      val resolver = new LanguageMapIdResolver(State[HumanLanguage](German).observable) {
        override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] = {
          assertEquals(id, descriptionId)
          resolutions.update(_ + 1)
          resolve()
        }
      }
      val client = pythonClient(observedReport(observation, "failed", "assertion"), _ => calls.update(_ + 1))
      grader.gradeStateSafe(source, forceGrading = true, List(client), resolver).map { result =>
        assertEquals(result.gradingStatus, GradingStatus.Incorrect())
        val info = result.additionalData.get
        assertEquals(info.programObservation.get.commandCount, 1)
        assertEquals(info.programObservation.get.commands, List(TurtleCommand[Double]("forward", List(2.0))))
        assertEquals(info.humanReadableTestExecutionDescription.tryGetInLanguage(German), expectedDescription)
        assertEquals(calls.now(), 1)
        assertEquals(resolutions.now(), 1)
        assert(!write(result).contains("PRIVATE_"))
      }
    }.map(_ => ())
  }

  test("observation values enforce their bounds when constructed or restored") {
    val prefix = (1 to 32).map(value => TurtleCommand[Double]("forward", List(value.toDouble))).toList
    val valid = ProgrammingTestProgramObservation(33, prefix)
    assertEquals(read[ProgrammingTestProgramObservation](write(valid)), valid)
    assertEquals(readBinary[ProgrammingTestProgramObservation](writeBinary(valid)), valid)
    List(9007199254740991d, -9007199254740991d).foreach { value =>
      val edge = ProgrammingTestProgramObservation(1, List(TurtleCommand[Double]("forward", List(value))))
      assertEquals(read[ProgrammingTestProgramObservation](write(edge)), edge)
      assertEquals(readBinary[ProgrammingTestProgramObservation](writeBinary(edge)), edge)
    }
    val invalid = List[() => ProgrammingTestProgramObservation](
      () => ProgrammingTestProgramObservation(-1, Nil),
      () => ProgrammingTestProgramObservation(10001, prefix),
      () => ProgrammingTestProgramObservation(0, prefix.take(1)),
      () => ProgrammingTestProgramObservation(33, prefix.dropRight(1)),
      () => ProgrammingTestProgramObservation(33, prefix :+ prefix.head),
      () => ProgrammingTestProgramObservation(1, List(TurtleCommand[Double]("fd", List(1.0)))),
      () => ProgrammingTestProgramObservation(1, List(TurtleCommand[Double]("receive_go"))),
      () => ProgrammingTestProgramObservation(1, List(TurtleCommand[Double]("forward"))),
      () => ProgrammingTestProgramObservation(1, List(TurtleCommand[Double]("forward", List(Double.NaN)))),
      () => ProgrammingTestProgramObservation(1, List(TurtleCommand[Double]("forward", List(Double.PositiveInfinity)))),
      () => ProgrammingTestProgramObservation(1, List(TurtleCommand[Double]("forward", List(9007199254740992d)))),
      () => ProgrammingTestProgramObservation(1, List(TurtleCommand[Double]("forward", List(1.0), List("private text")))))
    invalid.foreach(make => intercept[IllegalArgumentException](make()))
    val encoded = writeJs(valid).obj.toMap
    val malformed = List(
      ujson.Obj.from(encoded.updated("commandCount", ujson.Num(-1))),
      ujson.Obj.from(encoded.updated("commands", ujson.Arr())),
      ujson.Obj("commandCount" -> 1, "commands" -> ujson.Arr(ujson.Arr("forward", ujson.Arr(1), ujson.Arr("private text")))))
    malformed.foreach { value =>
      intercept[Exception](read[ProgrammingTestProgramObservation](value))
      intercept[Exception](readBinary[ProgrammingTestProgramObservation](writeBinary(value)))
    }
  }

  test("observation configuration and decoded reports survive the existing history codecs") {
    val descriptionId = LanguageMapContentId("tests/observed-count")
    val grader = ProgrammingUnitTestGrader("observed-count", "assert len(run_program()['commands']) == 34",
      publicDescription = Some(descriptionId), publishProgramObservation = true)
    assertEquals(writeJs(grader).obj("publishProgramObservation"), ujson.Bool(true))
    assertEquals(read[ProgrammingUnitTestGrader](write(grader)), grader)
    assertEquals(readBinary[ProgrammingUnitTestGrader](writeBinary(grader)), grader)
    assertEquals(read[ProgrammingTestGrader](write[ProgrammingTestGrader](grader)), grader)
    val exercise = ProgrammingExerciseFullJava("observed-history", Some(ProgrammingTestSuite(List(grader))),
      startingProgram = "public class Main { public static void main(String[] args) { for (int i = 1; i <= 33; i++) { Turtle.forward(i); } } }")
    val variable = exercise.interactionVariable
    val state = variable.currentValue
    val description = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      German -> "Prüft die Anzahl der Bewegungen.", English -> "Checks the movement count."))
    val resolver = new LanguageMapIdResolver(State[HumanLanguage](German).observable) {
      override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] = {
        assertEquals(id, descriptionId)
        Future.successful(description)
      }
    }
    val prefix = (1 to 32).map(value => ujson.Arr("forward", value))
    val client = pythonClient(observedReport(observationPayload(33, prefix), "failed", "assertion"))
    grader.gradeStateSafe(state, forceGrading = true, List(client), resolver).map { result =>
      assertEquals(result.gradingStatus, GradingStatus.Incorrect())
      val info = result.additionalData.get
      assertEquals(info.humanReadableTestExecutionDescription, description)
      assertEquals(info.programObservation.get.commandCount, 33)
      assertEquals(info.programObservation.get.commands.size, 32)
      variable.updateHistory(history => history.copy(gradings = Map(state -> GradedState(state, Set(result)))))
      val history = variable.history
      List(variable.deserializeHistory(variable.serializeHistory(history)),
        readBinary[InteractionVariableHistory[ProgrammingState, AdditionalProgrammingTestInfo, Any]](writeBinary(history))).foreach { restored =>
        assertEquals(restored, history)
        assertEquals(restored.gradings(state).testsWithResults.head.additionalData, Some(info))
      }
      val oldInfo = AdditionalProgrammingTestInfo(description)
      val oldFields = writeJs(oldInfo).obj.toMap - "programObservation"
      assertEquals(read[AdditionalProgrammingTestInfo](ujson.Obj.from(oldFields)), oldInfo)
      assertEquals(readBinary[AdditionalProgrammingTestInfo](writeBinary(ujson.Obj.from(oldFields))), oldInfo)
      assertEquals(oldInfo.programObservation, None)
    }
  }

  test("Java assertions use original method names and an explicit completed Turtle report") {
    val source = ProgrammingStateJavaString("public class Main { static void draw(int side) { Turtle.forward(side); } public static void main(String[] args) { draw(3); } }")
    val suite = SampleBeTest("assert run_java('draw', (3,))['commands'] == [['forward', 3]]")
    val client = pythonClient(ujson.Obj("status" -> "passed", "reason" -> ""), request => {
      assertEquals(request.context("_eva_tests"), ujson.Str(suite.pythonTestCode))
      val entryPoint = request.context("_eva_entry_point")
      assertEquals(entryPoint("functionName"), ujson.Str("java_turtle_run"))
      assertEquals(entryPoint("methods").obj.keySet, Set("draw"))
      assert(request.context("_eva_source").str.nonEmpty)
      assert(!request.context.contains("_eva_is_java") && !request.context.contains("_eva_java_methods"))
    })
    val states = List[ProgrammingState](source, source.toBeExpressionState, source.toPython, source.toSnapXml)
    Future.traverse(states) { state =>
      val saved = write[ProgrammingState](state)
      assertionGrader(suite).gradeStateSafe(state, forceGrading = true, List(client)).map { graded =>
        assertEquals(graded.gradedState, state)
        assertEquals(graded.gradingStatus, GradingStatus.Correct())
        assertEquals(write[ProgrammingState](state), saved)
      }
    }
  }

  test("invalid Java editing metadata is rejected before Python dispatch") {
    val calls = State(0)
    val client = pythonClient(ujson.Obj("status" -> "passed", "reason" -> ""), _ => calls.update(_ + 1))
    val grader = assertionGrader(SampleBeTest("assert run_java()['status'] == 'Completed'"))
    val states = List[ProgrammingState](ProgrammingStatePythonString("import java_turtle\n"),
      ProgrammingState.ProgrammingStateSnapXml("<project><notes>EduSquirrel Java Turtle 1\nnot json</notes></project>"))
    Future.traverse(states) { state =>
      val saved = write[ProgrammingState](state)
      grader.gradeStateSafe(state, forceGrading = true, List(client)).map { graded =>
        assert(graded.gradingStatus.isInstanceOf[GradingStatus.TestError])
        assertEquals(graded.gradedState, state)
        assertEquals(write[ProgrammingState](state), saved)
      }
    }.map(_ => assertEquals(calls.now(), 0))
  }

  test("full-program tests dispatch the shared report contract for independently authored languages") {
    val python = ProgrammingStatePythonString("fd(12)\r\nright(90)\r\nforward(3)\r\n")
    val java = ProgrammingStateJavaString(
      "public class Main { public static void main(String[] args) { Turtle.forward(12); Turtle.turnRight(90); Turtle.forward(3); } }")
    val snap = nativeSnap("""<script x="10" y="20"><block s="receiveGo"/>
      |<block s="forward"><l>12</l></block><block s="turn"><l>90</l></block>
      |<block s="forward"><l>3</l></block></script>""".stripMargin)
    val model = snap.toSnapScalaModel.fold(message => fail(message), identity)
    val states: List[(ProgrammingState, Boolean)] = List(python -> false, java -> true, snap -> false, model -> false)
    val test = "assert run_program()['commands'] == [['forward', 12], ['right', 90], ['forward', 3]]"
    val grader = ProgrammingUnitTestGrader("full-program", test)
    Future.traverse(states) { (state, checked) =>
      val saved = write[ProgrammingState](state)
      val fingerprint = state.fingerprint()
      val projection = state.testProgramProjection.fold(message => fail(message), identity)
      assertEquals(projection.commandEntryPoint.isDefined, checked)
      val calls = State(0)
      val client = pythonClient(ujson.Obj("status" -> "passed", "reason" -> ""), request => {
        calls.update(_ + 1)
        assertEquals(request.context("_eva_source"), ujson.Str(projection.source))
        assertEquals(request.context("_eva_tests"), ujson.Str(test))
        assertEquals(request.context("_eva_entry_point") != ujson.Null, checked)
        assertEquals(request.context("_eva_turtle_primitives")("fd"),
          ujson.Obj("command" -> "forward", "arity" -> 1, "inputs" -> ujson.Arr("Numeric")))
        assertEquals(request.context("_eva_turtle_primitives")("right"),
          ujson.Obj("command" -> "right", "arity" -> 1, "inputs" -> ujson.Arr("Numeric")))
        assert(request.resetGlobals && !request.captureStdout && !request.captureStderr)
        assertEquals(request.callbacks, Map.empty[String, List[String]])
        assertEquals(request.packages, Nil)
      })
      for {
        first <- grader.gradeStateSafe(state, forceGrading = true, List(client))
        second <- grader.gradeStateSafe(state, forceGrading = true, List(client))
      } yield {
        List(first, second).foreach { result =>
          assertEquals(result.gradedState, state)
          assertEquals(result.gradingStatus, GradingStatus.Correct())
          assertEquals(result.additionalData, None)
        }
        assertEquals(calls.now(), 2)
        assertEquals(state.fingerprint(), fingerprint)
        assertEquals(write[ProgrammingState](state), saved)
      }
    }.map(_ => ())
  }

  test("native Snap execution excludes loose blocks without changing the saved project") {
    val snap = nativeSnap("""<script><block s="receiveGo"/><block s="forward"><l>12</l></block></script>
      |<script x="300" y="200"><block s="forward"><l>999</l></block></script>""".stripMargin)
    val states: List[ProgrammingState] = List(snap, snap.toSnapScalaModel.fold(message => fail(message), identity))
    val grader = ProgrammingUnitTestGrader("started-script", "assert run_program()['commands'] == [['forward', 12]]")
    Future.traverse(states) { state =>
      val saved = write[ProgrammingState](state)
      val projection = state.testProgramProjection.fold(message => fail(message), identity)
      assertEquals(projection.commandEntryPoint, None)
      assert(!projection.source.contains("999"))
      val calls = State(0)
      val client = pythonClient(ujson.Obj("status" -> "passed", "reason" -> ""), request => {
        calls.update(_ + 1)
        assertEquals(request.context("_eva_entry_point"), ujson.Null)
        assert(!request.context("_eva_source").str.contains("999"))
      })
      grader.gradeStateSafe(state, forceGrading = true, List(client)).map { result =>
        assertEquals(result.gradedState, state)
        assertEquals(result.gradingStatus, GradingStatus.Correct())
        assertEquals(calls.now(), 1)
        assertEquals(write[ProgrammingState](state), saved)
        assert(state.toSnapXml.snapXml.contains("999"))
      }
    }.map(_ => ())
  }

  test("unsupported or ambiguous Snap execution cannot grade a partial program") {
    val unknown = nativeSnap("""<script><block s="receiveGo"/><block s="forward"><l>12</l></block>
      |<block s="unsupportedPrimitive"><l>1</l></block></script>""".stripMargin)
    val concurrent = nativeSnap("""<script><block s="receiveGo"/><block s="forward"><l>12</l></block></script>
      |<script><block s="receiveGo"/><block s="forward"><l>3</l></block></script>""".stripMargin)
    val legacy = nativeSnap("<script><block s=\"receiveGo\"/></script>")
      .copy(legacyFloatingObjects = List("opaque block data"))
    val invalidScripts = List(
      """<script><unknown/><block s="receiveGo"/><block s="forward"><l>12</l></block></script>""",
      """<script><block s="receiveGo"><l>1</l></block></script>""",
      """<script><block s="receiveGo"/><block s="forward"><l>12</l><l>99</l></block></script>""",
      """<script><block s="receiveGo"/><block s="forward"><variables/><l>12</l></block></script>""",
      """<script><block s="receiveGo"/><block s="forward" var="x"><l>12</l></block></script>""",
      """<script><block s="receiveGo"/><block s="doFor"><l>i</l><l>3</l><l>1</l><script><block s="forward"><block var="i"/></block></script></block></script>""",
      """<script><block s="receiveGo"/><block s="doRepeat"><l>3</l><script><block s="forward"><l>12</l></block></script></block></script>"""
    ).map(nativeSnap)
    val doubleStage = nativeSnap("<script><block s=\"receiveGo\"/></script>")
      .copy(snapXml = nativeSnap("").snapXml.replace("</project>", "<stage/></project>"))
    val mixedStage = nativeSnap("").copy(snapXml = nativeSnap("").snapXml.replace("</project>", "<scenes/></project>"))
    val doubleScripts = nativeSnap("").copy(snapXml = nativeSnap("").snapXml.replace("<scripts></scripts>",
      "<scripts/><scripts><script><block s=\"receiveGo\"/><block s=\"forward\"><l>12</l></block></script></scripts>"))
    val states: List[ProgrammingState] = (List(unknown, concurrent, legacy, doubleStage, mixedStage, doubleScripts) ++ invalidScripts).flatMap { state =>
      List[ProgrammingState](state, state.toSnapScalaModel.fold(message => fail(message), identity))
    } :+ ProgrammingState.ProgrammingStateSnapXml("<project><stage>")
    val grader = ProgrammingUnitTestGrader("complete-only", "assert run_program()['commands'] == [['forward', 12]]")
    val calls = State(0)
    val client = pythonClient(ujson.Obj("status" -> "passed", "reason" -> ""), _ => calls.update(_ + 1))
    Future.traverse(states) { state =>
      val saved = write[ProgrammingState](state)
      assert(state.testProgramProjection.isLeft)
      grader.gradeStateSafe(state, forceGrading = true, List(client)).map { result =>
        assert(result.gradingStatus.isInstanceOf[GradingStatus.TestError])
        assertEquals(result.gradedState, state)
        assertEquals(result.additionalData, None)
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
        if index == 5 then GradingImportance.HIDDEN_TEST else GradingImportance.REQUIRED_TEST,
        if index == 2 then Some(LanguageMapContentId("tests/second-public-check")) else None)
    }.toList
    val suite: BeTestSuite = ProgrammingTestSuite(graders)
    assertEquals(read[BeTestSuite](write(suite)), suite)
    assertEquals(readBinary[BeTestSuite](writeBinary(suite)), suite)
    val described = graders(1).asInstanceOf[ProgrammingUnitTestGrader]
    assertEquals(read[Option[LanguageMapContentId]](writeJs(described).obj("publicDescription")), described.publicDescription)
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
      val binary: WorkbookInteractionElementFull[ProgrammingState, AdditionalProgrammingTestInfo, Any] = exercise match {
        case value: ProgrammingExercise => readBinary[ProgrammingExercise](writeBinary(value))
        case value: ProgrammingExerciseFullJava => readBinary[ProgrammingExerciseFullJava](writeBinary(value))
        case _ => fail("Unexpected exercise type")
      }
      assertEquals(binary.gradingElements, graders)
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
    val stateJson = ujson.read("""{"$type":"PythonSource","value":{"$type":"ProgrammingState.ProgrammingState.ProgrammingStatePythonString","code":"pass\r\n"}}""")
    val state: ProgrammingState = ProgrammingStatePythonString("pass\r\n")
    val time = LocalDateTime.parse("2026-10-26T12:00")
    fixtures.foreach { (json, importance) =>
      val expected = assertionGrader(SampleBeTest("assert True\r\nassert False\n"), importance)
      assertEquals(expected.publicDescription, None)
      assertEquals(expected.publishProgramObservation, false)
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
      ujson.Obj("testSuite" -> suite, "publicDescription" -> writeJs(Some(LanguageMapContentId("tests/new-description")))),
      ujson.Obj("testSuite" -> suite, "publishProgramObservation" -> true),
      ujson.Obj("testSuite" -> ujson.Obj("$type" -> "UnknownSuite", "pythonTestCode" -> "assert True")),
      ujson.Obj("testSuite" -> ujson.Obj("$type" -> "ProgrammingTestSuite", "graders" -> ujson.Arr())),
      ujson.Obj("testSuite" -> ujson.Obj("$type" -> "SampleBeTest", "pythonTestCode" -> "assert True", "extra" -> 1)),
      ujson.Obj("testSuite" -> ujson.Null),
      ujson.Obj("testId" -> "new", "pythonTestCode" -> "assert True", "unexpected" -> true),
      ujson.Obj("testId" -> "new", "pythonTestCode" -> "assert True", "publicDescription" -> true),
      ujson.Obj("testId" -> "new", "pythonTestCode" -> "assert True", "publishProgramObservation" -> "true"),
      ujson.Obj("testId" -> "new", "pythonTestCode" -> "assert True", "publishProgramObservation" -> 1),
      ujson.Obj("testId" -> "new", "pythonTestCode" -> "assert True", "publishProgramObservation" -> ujson.Null),
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
