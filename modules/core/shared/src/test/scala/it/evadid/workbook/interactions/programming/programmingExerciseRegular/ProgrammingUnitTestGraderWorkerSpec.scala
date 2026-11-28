package it.evadid.workbook.interactions.programming.programmingExerciseRegular

import it.evadid.workbook.support.OfflineController
import it.evadid.workbook.sync.UpdateImportance

import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage, Lisp, Python}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.state.State
import it.evadid.distribution.clients.LocalExecutionClient
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.distribution.commandTypes.WorkerCommands.{RunPythonRequest, RunPythonResult}
import it.evadid.util.logging.Logger
import it.evadid.vm.test.ProgrammingTestSuite
import it.evadid.workbook.abstractions.grading.{GradingImportance, GradingStatus}
import it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite
import upickle.default.{read, write}

import scala.concurrent.{ExecutionContext, Future}

class ProgrammingUnitTestGraderWorkerSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val description = LanguageMapContentId("test/checkDescription")
  private val answer = ProgrammingStateRaw(Python, "def area(w, h):\n    return w * h\n")

  private final class Worker(report: ujson.Value) extends LocalExecutionClient[RunPythonRequest, RunPythonResult] {
    val requests = State(Vector.empty[RunPythonRequest])
    override def canExecuteCommand(command: ExecutionCommand): Boolean = command.name == WorkerCommands.runPython.name
    override protected def rawExecution(logger: Logger, input: RunPythonRequest): Future[RunPythonResult] = {
      requests.update(_ :+ input)
      Future.successful(RunPythonResult(report, "", "", Nil))
    }
  }
  private def report(status: String, reason: String, extra: (String, ujson.Value)*): ujson.Value =
    ujson.Obj.from(Seq("status" -> ujson.Str(status), "reason" -> ujson.Str(reason)) ++ extra)

  private val resolver = new LanguageMapIdResolver(State[HumanLanguage](English).observable) {
    override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] =
      Future.successful(LanguageMap.mapBasedLanguageMap(Map(English -> "Checks the area.", German -> "Prüft die Fläche.")))
  }

  test("worker outcomes become correct, incorrect and test-error grades") {
    val grader = ProgrammingUnitTestGrader("area", "assert area(2, 3) == 6")
    for {
      passed <- grader.gradeState(answer, Seq(new Worker(report("passed", ""))))
      failed <- grader.gradeState(answer, Seq(new Worker(report("failed", "assertion"))))
      error <- grader.gradeState(answer, Seq(new Worker(report("error", "invalid-tests"))))
      unsupported <- grader.gradeState(ProgrammingStateRaw(Lisp, "(forward 1)"), Nil)
    } yield {
      assertEquals(passed.gradingStatus, GradingStatus.Correct)
      assertEquals(passed.additionalData, None)
      assertEquals(failed.gradingStatus, GradingStatus.Incorrect)
      error.gradingStatus match {
        case GradingStatus.TestError(message) => assert(message.contains("definition is invalid"), message)
        case other => fail(s"Unexpected $other")
      }
      unsupported.gradingStatus match {
        case GradingStatus.TestError(message) => assert(message.contains("does not support"), message)
        case other => fail(s"Unexpected $other")
      }
    }
  }

  test("public checks publish observations, invocations and a resolved description; hidden checks publish nothing") {
    val observation = ujson.Obj("commandCount" -> 1, "commands" -> ujson.Arr(ujson.Arr("forward", 10)))
    val invocation = ujson.Obj("target" -> ujson.Null, "arguments" -> ujson.Arr())
    val failedReport = report("failed", "assertion", "programObservation" -> observation, "testInvocation" -> invocation)
    val public = ProgrammingUnitTestGrader("area", "assert area(2, 3) == 6", GradingImportance.REQUIRED_TEST, Some(description),
      publishProgramObservation = true, publishInvocationArguments = true)
    val hidden = public.copy(importance = GradingImportance.HIDDEN_TEST)
    val worker = new Worker(failedReport)
    for {
      shown <- public.gradeStateSafe(answer, forceGrading = true, UpdateImportance.MAJOR, TestExecutionContext(List(worker), languageMapIdResolver = Some(resolver)))
      secret <- hidden.gradeState(answer, Seq(new Worker(report("failed", "assertion"))), resolver)
    } yield {
      val info = shown.additionalData.get
      assertEquals(info.humanReadableTestExecutionDescription.tryGetInLanguage(German), Some("Prüft die Fläche."))
      assertEquals(info.programObservation.map(_.commandCount), Some(1))
      assert(info.testInvocation.nonEmpty)
      assertEquals(worker.requests.now().head.context("_eva_observe_program"), ujson.Bool(true))
      assertEquals(secret.gradingStatus, GradingStatus.Incorrect)
      assertEquals(secret.additionalData, None)
    }
  }

  test("graders round-trip through their strict codec and reject unknown or malformed settings") {
    val grader = ProgrammingUnitTestGrader("area", "assert True", GradingImportance.OPTIONAL_TEST, Some(description),
      publishProgramObservation = true, publishInvocationArguments = true, publishCommandComparison = true)
    assertEquals(read[ProgrammingUnitTestGrader](write(grader)), grader)
    val tagged = ujson.read(write(grader))
    tagged("serializedType") = "ProgrammingUnitTestGrader"
    assertEquals(read[ProgrammingUnitTestGrader](tagged), grader)
    intercept[Exception](read[ProgrammingUnitTestGrader]("""{"testId":"a","pythonTestCode":"x","serializedType":"Other"}"""))
    intercept[Exception](read[ProgrammingUnitTestGrader]("""{"testId":"a","pythonTestCode":"x","surprise":1}"""))
    intercept[Exception](read[ProgrammingUnitTestGrader]("""{"testId":"a","pythonTestCode":"x","publishProgramObservation":"yes"}"""))
    intercept[Exception](read[ProgrammingUnitTestGrader]("""{"testId":"a","pythonTestCode":"x","publishInvocationArguments":1}"""))
    intercept[Exception](read[ProgrammingUnitTestGrader]("""{"testId":"a","pythonTestCode":"x","publishCommandComparison":null}"""))
    intercept[IllegalArgumentException](ProgrammingUnitTestGrader(" a", "x"))
    intercept[IllegalArgumentException](ProgrammingUnitTestGrader("a", "x", publishCommandComparison = true))
  }

  test("hints include the latest public check results of the current answer") {
    val grader = ProgrammingUnitTestGrader("area", """assert run_java("area", [2, 3])["commands"] == [["forward", 6]]""", publicDescription = Some(description),
      publishInvocationArguments = true)
    val exercise = ProgrammingExerciseFullJava("graded-hint", Some(ProgrammingTestSuite(List(grader))),
      startingProgram = """public class Main {
                          |  public static void main(String[] args) {}
                          |  static void area(int width, int height) { Turtle.forward(width + height); }
                          |}""".stripMargin,
      hintTask = Some(LanguageMapContentId("test/task")))
    final class Llm extends LocalExecutionClient[it.evadid.distribution.commandTypes.LLMCommands.FeedbackLlmRequest, String] {
      val prompts = State(Vector.empty[String])
      override def canExecuteCommand(command: ExecutionCommand): Boolean =
        command.name == it.evadid.distribution.commandTypes.LLMCommands.feedbackLlmCommandFactory.name
      override protected def rawExecution(logger: Logger, input: it.evadid.distribution.commandTypes.LLMCommands.FeedbackLlmRequest): Future[String] = {
        prompts.update(_ :+ input.prompt)
        Future.successful("""{"hints":[{"text":"Which line computes the result?","evidenceIds":["check-1"]}]}""")
      }
    }
    val llm = new Llm
    val call = ujson.Obj("target" -> "area", "arguments" -> ujson.Arr(
      ujson.Obj("kind" -> "int", "value" -> "2"), ujson.Obj("kind" -> "int", "value" -> "3")))
    val context = TestExecutionContext(List(new Worker(report("failed", "assertion", "testInvocation" -> call)), llm),
      languageMapIdResolver = Some(resolver))
    val state = exercise.defaultValue
    grader.gradeStateSafe(state, forceGrading = true, UpdateImportance.MAJOR, context).flatMap { result =>
      assertEquals(result.gradingStatus, GradingStatus.Incorrect)
      assert(result.additionalData.flatMap(_.testInvocation).exists(_.target.contains("area")))
      val answers = OfflineController.of(exercise)
      answers.updateHistory(exercise)(_.addGrading(result))
      exercise.scaffoldingElement.get.handleFeedbackGeneration(state, answers.feedback(exercise).evidenceFor(state), context).map { _ =>
        val check = ujson.read(llm.prompts.now().head)("checks")(0)
        assertEquals(check("id").str, "check-1")
        assertEquals(check("status").str, "incorrect")
        assertEquals(check("required").bool, true)
        assertEquals(check("description").str, "Checks the area.")
        assertEquals(check("testCall")("method").str, "area")
        assertEquals(check("testCall")("arguments").arr.map(_("value").str).toList, List("2", "3"))
        assert(!check("testCall").obj.contains("firstDifference"))
      }
    }
  }
}
