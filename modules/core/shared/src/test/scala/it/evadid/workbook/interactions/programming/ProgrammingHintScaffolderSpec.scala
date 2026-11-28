package it.evadid.workbook.interactions.programming

import it.evadid.workbook.support.OfflineController
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage, Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.state.State
import it.evadid.distribution.clients.{ExecutionClient, LocalExecutionClient}
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.llm.LlmConfiguration
import it.evadid.util.logging.Logger
import it.evadid.vm.test.SampleBeTest
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus}
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus
import it.evadid.workbook.abstractions.InteractionFormat.ProgrammingExerciseWithLlmScaffoldingAndGrading
import it.evadid.workbook.interactions.programming.programmingExerciseRegular.ProgrammingExerciseFullJava
import it.evadid.workbook.interactions.programming.state.*
import it.evadid.workbook.control.InteractionController
import it.evadid.workbook.sync.{SyncConnection, UpdateImportance}
import it.evadid.workbook.abstractions.ExecutionContextInformation
import it.evadid.workbook.support.TestExecutionContext
import java.time.LocalDateTime
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future}

/** A hint request for a Java exercise reaches the LLM backend as one well-formed feedback command. */
class ProgrammingHintScaffolderSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val task = LanguageMapContentId("exercise/sum/task")
  private val source =
    """public class Main {
      |  static int sum(int a, int b) {
      |    return a - b;
      |  }
      |
      |  public static void main(String[] args) {
      |  }
      |}""".stripMargin
  private val reply = """{"hints":[{"text":"What does `sum` return when a is smaller than b?","evidenceIds":["source-3","task"]}]}"""

  /** The LLM backend double: requests arrive through the real command codec; each answer is the next reply. */
  private class LlmBackend(replies: String*) extends LocalExecutionClient[LLMCommands.FeedbackLlmRequest, String] {
    val routed = State(Set.empty[String])
    val received = State(Vector.empty[LLMCommands.FeedbackLlmRequest])
    private val remaining = State(replies.toList)
    override def canExecuteCommand(command: ExecutionCommand): Boolean = {
      routed.update(_ + command.name)
      command.name == LLMCommands.feedbackLlmCommandFactory.name
    }
    override protected def rawExecution(logger: Logger, request: LLMCommands.FeedbackLlmRequest): Future[String] = {
      received.update(_ :+ request)
      val answer = remaining.now().headOption.getOrElse("")
      remaining.update(_.drop(1))
      Future.successful(answer)
    }
    def requests: Vector[LLMCommands.FeedbackLlmRequest] = received.now()
    def prompts: Vector[ujson.Value] = requests.map(request => ujson.read(request.prompt))
  }

  /** The control records a failed hint as an error result for the answer, with the reason. */
  private def rejected(result: it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult[ProgrammingState, ?]): Boolean =
    result.scaffoldingStatus match {
      case ScaffoldingStatus.ScaffoldingError(message) => message.contains("does not match the expected format")
      case _ => false
    }

  private val taskTexts = Map[HumanLanguage, String](
    German -> "Gib die Summe zweier positiver Zahlen zurück.", English -> "Return the sum of two positive integers.")

  private def resolver(language: HumanLanguage, texts: Map[HumanLanguage, String] = taskTexts) =
    new LanguageMapIdResolver(State[HumanLanguage](language).observable) {
      override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] =
        if id == task then Future.successful(LanguageMap.mapBasedLanguageMap(texts))
        else if id == LanguageMapContentId("basic/programmingHintNoChange") then
          Future.successful(LanguageMap.mapBasedLanguageMap(Map(German -> "Zu diesem Stand habe ich keinen weiteren Hinweis.")))
        else Future.failed(java.util.NoSuchElementException(id.fullId))
    }

  /** A Java exercise holding `answer` in an offline controller of its own. */
  private final case class Answered(element: ProgrammingExerciseFullJava, answers: InteractionController) {
    def grader = element.gradingElements.head
    def evidence(state: ProgrammingState) = answers.feedback(element).evidenceFor(state)
    def addGrading(result: GradingResult[ProgrammingState, InteractionFormat.Grading[ProgrammingExerciseWithLlmScaffoldingAndGrading.type]]): Unit =
      answers.updateHistory(element)(_.addGrading(result))
    def hint(execution: ExecutionContextInformation) = {
      answers.connect(SyncConnection.offline.copy(execution = execution))
      answers.feedback(element).getOrCalculateScaffolding()
    }
  }
  private def exercise(answer: ProgrammingState, tests: Boolean = false): Answered = {
    val element = ProgrammingExerciseFullJava(s"java-${java.util.UUID.randomUUID()}",
      Option.when(tests)(SampleBeTest("assert sum(1, 2) == 3")), hintTask = Some(task))
    val answers = OfflineController.of(element)
    answers.setStateFromUserInteraction(element, answer, UpdateImportance.MAJOR)
    Answered(element, answers)
  }
  private def context(backend: ExecutionClient, language: HumanLanguage = German, configuration: Option[LlmConfiguration] = None,
      texts: Map[HumanLanguage, String] = taskTexts) =
    TestExecutionContext(List(backend), languageMapIdResolver = Some(resolver(language, texts)), llmConfiguration = configuration)

  test("a Java hint sends one feedback command whose prompt holds the task, numbered source lines and no checks") {
    val backend = new LlmBackend(reply)
    val element = exercise(ProgrammingStateRaw(Java, source))
    val configuration = Some(LlmConfiguration("https://llm.example/chat", "tutor-model", Some("session-key")))
    element.hint(context(backend, configuration = configuration)).map { result =>
      assertEquals(backend.routed.now(), Set("feedback-llm-request"))
      assertEquals(backend.requests.size, 1)
      val request = backend.requests.head
      assertEquals(request.llmConfiguration, configuration, "the session's own model configuration is forwarded")
      assert(request.systemPrompt.contains("\"evidenceIds\"") && request.systemPrompt.contains("{\"hints\":[]}"))
      val prompt = backend.prompts.head
      assertEquals(prompt("feedbackLanguage").str, German.name)
      assertEquals(prompt("task"), ujson.Obj("id" -> "task", "text" -> "Gib die Summe zweier positiver Zahlen zurück."))
      assertEquals(prompt("source")("language").str, Java.name)
      assertEquals(prompt("source")("code").str, source)
      val lines = prompt("source")("lines").arr.map(line => line("id").str -> line("text").str).toList
      assertEquals(lines.map(_._1), List("source-1", "source-2", "source-3", "source-4", "source-6", "source-7", "source-8"),
        "blank lines get no id; ids keep the editor's line numbers")
      assertEquals(lines.find(_._1 == "source-3").map(_._2), Some("    return a - b;"))
      assertEquals(prompt("checks").arr.toList, Nil)
      assertEquals(prompt.obj.keySet.toSet, Set("feedbackLanguage", "task", "source", "checks"))

      assertEquals(result.forState, ProgrammingStateRaw(Java, source): ProgrammingState)
      assertEquals(result.scaffoldingStatus, ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.mapBasedLanguageMap(Map(
        German -> "1. What does sum return when a is smaller than b?"))), "identifier backticks are removed")
      val accepted = ujson.read(result.additionalInformation.get.completeHistory.messages.head.text)
      assertEquals(accepted("hints")(0)("evidenceIds").arr.map(_.str).toList, List("source-3", "task"))
    }
  }

  test("the stored hint is reused for the same answer without a second command") {
    val backend = new LlmBackend(reply)
    val element = exercise(ProgrammingStateRaw(Java, source))
    val ctx = context(backend)
    for {
      first <- element.hint(ctx)
      second <- element.hint(ctx)
    } yield {
      assertEquals(second, first)
      assertEquals(backend.requests.size, 1)
    }
  }

  test("the task falls back to English, and structured Java and Python answers are sent as their source text") {
    val backend = new LlmBackend(reply.replace("source-3", "source-1"), reply.replace("source-3", "source-1"))
    val structured = ProgrammingStateJavaStructured(source)
    val python = ProgrammingStateRaw(Python, "def total(a, b):\n    return a - b\n")
    val englishOnly = Map[HumanLanguage, String](English -> "Return the sum.")
    for {
      _ <- exercise(structured).hint(context(backend, texts = englishOnly))
      _ <- exercise(python).hint(context(backend, texts = englishOnly))
    } yield {
      val prompts = backend.prompts
      assertEquals(prompts.map(_("task")("text").str), Vector("Return the sum.", "Return the sum."))
      assertEquals(prompts.map(_("feedbackLanguage").str), Vector(German.name, German.name), "the learner's language stays the feedback language")
      assertEquals(prompts(0)("source")("code").str, structured.toString)
      assertEquals(prompts(1)("source")("language").str, Python.name)
      assertEquals(prompts(1)("source")("code").str, "def total(a, b):\n    return a - b\n")
    }
  }

  test("public check results are part of the prompt and may be cited") {
    val backend = new LlmBackend("""{"hints":[{"text":"Which result does check one report for this method?","evidenceIds":["check-1"]}]}""")
    val answer = ProgrammingStateRaw(Java, source)
    val element = exercise(answer, tests = true)
    element.addGrading(GradingResult(element.grader, answer, GradingStatus.Incorrect, None, None, LocalDateTime.now()))
    element.hint(context(backend, language = English)).map { result =>
      val checks = backend.prompts.head("checks").arr.toList
      assertEquals(checks, List(ujson.Obj("id" -> "check-1", "required" -> true, "status" -> "incorrect")))
      assertEquals(result.scaffoldingStatus, ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.mapBasedLanguageMap(Map(
        English -> "1. Which result does check one report for this method?"))))
    }
  }

  test("a passing public requirement skips the model; an ungraded answer may receive no hint") {
    val backend = new LlmBackend("""{"hints":[]}""")
    val answer = ProgrammingStateRaw(Python, "def total(a, b):\n    return a + b")
    val graded = exercise(answer, tests = true)
    graded.addGrading(GradingResult(graded.grader, answer, GradingStatus.Correct))
    for {
      passed <- graded.hint(context(backend))
      _ = assertEquals(backend.requests.size, 0)
      noEvidence <- exercise(answer).hint(context(backend))
    } yield {
      assertEquals(backend.requests.size, 1)
      assertEquals(passed.scaffoldingStatus, noEvidence.scaffoldingStatus)
      assertEquals(passed.scaffoldingEntity, it.evadid.workbook.abstractions.FeedbackEntity.TestEntity)
      assertEquals(noEvidence.scaffoldingEntity, it.evadid.workbook.abstractions.FeedbackEntity.SystemEntity)
    }
  }

  test("a hint can explain a public mismatch without asking a question") {
    val backend = new LlmBackend("""{"hints":[{"text":"Trace the two arguments through the return expression.","evidenceIds":["check-1","source-3"]}]}""")
    val answer = ProgrammingStateRaw(Java, source)
    val element = exercise(answer, tests = true)
    val feedback = LanguageMap.mapBasedLanguageMap(Map[HumanLanguage,String](English -> "The result was negative."))
    element.addGrading(GradingResult(element.grader, answer, GradingStatus.Incorrect, feedback, None))
    element.hint(context(backend, language = English)).map { result =>
      assertEquals(backend.prompts.head("checks")(0)("feedback").str, "The result was negative.")
      assertEquals(result.scaffoldingStatus, ScaffoldingStatus.ScaffoldingFeedback(
        LanguageMap.mapBasedLanguageMap(Map(English -> "1. Trace the two arguments through the return expression."))))
    }
  }

  test("a check without a result is listed as unavailable and cannot be cited") {
    val backend = new LlmBackend("""{"hints":[{"text":"Why does check one fail?","evidenceIds":["check-1"]}]}""")
    exercise(ProgrammingStateRaw(Java, source), tests = true).hint(context(backend)).map { result =>
        assertEquals(backend.prompts.head("checks")(0), ujson.Obj("id" -> "check-1", "required" -> true, "status" -> "unavailable"))
        assert(rejected(result), result.scaffoldingStatus.toString)
      }
  }

  test("replies outside the hint contract are rejected") {
    val invalid = List(
      """{"hints":[{"text":"Check source-3","evidenceIds":["source-3"]}]}""",
      """{"hints":[{"text":"Which line?","evidenceIds":["source-99"]}]}""",
      """{"hints":[{"text":"Which line?","evidenceIds":["source-3"],"answer":"line 3"}]}""",
      """{"hints":[{"text":"","evidenceIds":["task"]}]}""",
      """Sure! Here is a hint: which line?""")
    Future.traverse(invalid) { raw =>
      val backend = new LlmBackend(raw)
      exercise(ProgrammingStateRaw(Java, source)).hint(context(backend))
        .map(result => raw -> rejected(result))
    }.map(outcomes => assertEquals(outcomes.filterNot(_._2), Nil))
  }

  test("a Snap answer is discussed as its Python form") {
    val backend = new LlmBackend(reply.replace("source-3", "source-1"))
    val snapAnswer: ProgrammingState = ProgrammingStateRaw(Python, "forward(10)\n").toSnapScalaModel.toOption.get
    exercise(snapAnswer).hint(context(backend)).map { _ =>
      val prompt = backend.prompts.head
      assertEquals(prompt("source")("language").str, Python.name)
      assertEquals(prompt("source")("code").str, snapAnswer.toPython.code)
    }
  }

  test("a turtle exercise with a public task offers hints; one without a task does not") {
    val backend = new LlmBackend(reply.replace("source-3", "source-1"))
    val target = it.evadid.workbook.interactions.programming.programmingExerciseTurtle.TurtleGraphic(Seq.empty[it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand[Double]])
    val shape = it.evadid.workbook.interactions.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction(
      s"turtle-${java.util.UUID.randomUUID()}", ProgrammingStateRaw(Python, "forward(10)\n"), target, instructionText = Some(task))
    assertEquals(shape.copy(elementId = "no-task", instructionText = None).scaffoldingElement, None)
    OfflineController.of(context(backend), shape).feedback(shape).getOrCalculateScaffolding().map { _ =>
      assertEquals(backend.prompts.map(_("task")("text").str), Vector("Gib die Summe zweier positiver Zahlen zurück."))
      assertEquals(backend.prompts.head("source")("code").str, "forward(10)\n")
    }
  }

  test("a matching drawing still allows a hint about a required method") {
    import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
    import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.*
    val answer = ProgrammingStateRaw(Python, "forward(10)\n")
    val shape = TurtleRecreateShapeInteraction("hint-same-drawing", answer,
      TurtleGraphic(List(TurtleCommand.Forward(10.0))), instructionText = Some(task))
    val backend = new LlmBackend("""{"hints":[{"text":"Compare where the drawing happens with the requested method.","evidenceIds":["source-1","task"]}]}""")
    val ctx = context(backend, language = English, texts = Map(English -> "Use a helper method to draw a line."))
    val grader = shape.gradingElements.head
    val answers = OfflineController.of(shape)
    for {
      grade <- grader.gradeStateSafe(answer, true, UpdateImportance.MAJOR, ctx)
      _ = assertEquals(grade.gradingStatus, GradingStatus.Correct)
      _ = answers.updateHistory(shape)(_.addGrading(grade))
      hint <- shape.scaffoldingElement.get.handleFeedbackGeneration(answer, answers.feedback(shape).evidenceFor(answer), ctx)
    } yield {
      assert(!hint.scaffoldingStatus.isError)
      assertEquals(backend.prompts.head("checks")(0)("scope").str, "drawing")
      assertEquals(backend.prompts.head("checks")(0)("status").str, "correct")
    }
  }

  test("no command is sent without a task resolver or without an LLM client") {
    val backend = new LlmBackend(reply)
    val java = exercise(ProgrammingStateRaw(Java, source))
    val scaffolder = java.element.scaffoldingElement.get
    for {
      noResolver <- scaffolder.handleFeedbackGeneration(ProgrammingStateRaw(Java, source), java.evidence(ProgrammingStateRaw(Java, source)), TestExecutionContext(List(backend))).failed
      noClient <- scaffolder.handleFeedbackGeneration(ProgrammingStateRaw(Java, source), java.evidence(ProgrammingStateRaw(Java, source)),
        TestExecutionContext(Nil, languageMapIdResolver = Some(resolver(German)))).failed
    } yield {
      assert(noResolver.getMessage.contains("LanguageMapIdResolver"), noResolver.getMessage)
      assert(noClient.getMessage.contains("No execution client"), noClient.getMessage)
      assertEquals(backend.requests, Vector.empty[LLMCommands.FeedbackLlmRequest])
    }
  }

  test("a hint is not accepted when the answer changed while the request was running") {
    val element = ProgrammingExerciseFullJava("java-changing", hintTask = Some(task))
    val answers = OfflineController.of(element)
    answers.setStateFromUserInteraction(element, ProgrammingStateRaw(Java, source), UpdateImportance.MAJOR,
      timestamp = LocalDateTime.now().minusSeconds(1))
    val backend = new LlmBackend(reply) {
      override protected def rawExecution(logger: Logger, request: LLMCommands.FeedbackLlmRequest): Future[String] = {
        answers.setStateFromUserInteraction(element, ProgrammingStateRaw(Java, source + "\n// edited"),
          UpdateImportance.MAJOR)
        super.rawExecution(logger, request)
      }
    }
    element.scaffoldingElement.get.handleFeedbackGeneration(ProgrammingStateRaw(Java, source), answers.feedback(element).evidenceFor(ProgrammingStateRaw(Java, source)), context(backend)).failed.map { error =>
      assertEquals(backend.requests.size, 1)
      assert(error.getMessage.contains("changed"), error.getMessage)
    }
  }

  test("oversized sources are refused before any command is sent") {
    val backend = new LlmBackend(reply)
    val long = ProgrammingStateRaw(Java, (1 to 401).map(i => s"int v$i = $i;").mkString("\n"))
    { val longExercise = exercise(long); longExercise.element.scaffoldingElement.get.handleFeedbackGeneration(long, longExercise.evidence(long), context(backend)) }.failed.map { error =>
      assert(error.getMessage.contains("too many lines"), error.getMessage)
      assertEquals(backend.requests.size, 0)
    }
  }
}
