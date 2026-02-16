package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.others.BeStartProgram
import contentmanagement.model.vm.parsing.python.PythonParser
import interactionPlugins.pythonExercises.{
  PythonRunRequest,
  PythonRunResult,
  PythonRunStatus,
  PythonTestResult => RuntimeTestResult,
  PythonTestStatus
}
import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future, Promise}

final class BlockFeedbackTestRunnerSpec extends FunSuite:

  private given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private def exprFromPython(source: String): BeExpression =
    BeStartProgram(PythonParser.parsePython(source))

  private def dummyExerciseText: LanguageMap[HumanLanguage] =
    LanguageMap.mapBasedLanguageMap(
      Map[HumanLanguage, String](
        AppLanguage.English -> "Dummy exercise text"
      )
    )

  test("executeWithRunner builds PythonRunRequest from request.pythonSource and plan") {
    val request = BlockFeedbackRequest(
      exerciseText = dummyExerciseText,
      studentCodePython = exprFromPython("x = 1\nprint(x)"),
      submissionNr = 1,
      config = BlockFeedbackConfig.default.copy(enableUnitTests = true, runHiddenOnlyIfVisiblePass = false),
      humanLanguage = AppLanguage.English
    )

    val t1 = BlockFeedbackPythonTest(name = "t1", code = "assert True")
    val t2 = BlockFeedbackPythonTest(name = "t2", code = "assert True", hint = Some("hint-2"))

    val plan = BlockFeedbackTestPlan(
      visibleTests = Seq(t1),
      hiddenTests = Seq(t2),
      fixtures = Nil,
      packages = Seq("micropip"),
      timeoutMs = 1234,
      derivedHints = Nil
    )

    var captured: Vector[PythonRunRequest] = Vector.empty

    val runner: PythonRunRequest => Future[PythonRunResult] = (req: PythonRunRequest) =>
      captured = captured :+ req
      Future.successful(
        PythonRunResult(
          status = PythonRunStatus.Success,
          tests = Seq.empty,
          stdout = "",
          stderr = "",
          error = None,
          score = 1.0
        )
      )

    BlockFeedbackTestRunner
      .executeWithRunner(request, plan, runner)
      .map { _ =>
        assertEquals(captured.size, 2)

        val visibleReq = captured.head
        assertEquals(visibleReq.code.trim, request.pythonSource.trim)
        assertEquals(visibleReq.visibleTests.map(_.name), Seq("t1"))
        assertEquals(visibleReq.hiddenTests, Nil)
        assertEquals(visibleReq.packages, Seq("micropip"))
        assertEquals(visibleReq.timeoutMs, 1234)

        val hiddenReq = captured(1)
        assertEquals(hiddenReq.code.trim, request.pythonSource.trim)
        assertEquals(hiddenReq.visibleTests, Nil)
        assertEquals(hiddenReq.hiddenTests.map(_.name), Seq("t2"))
        assertEquals(hiddenReq.packages, Seq("micropip"))
        assertEquals(hiddenReq.timeoutMs, 1234)
      }
  }

  test("executeWithRunner maps runtime tests into feedback PythonTestResult") {
    val request = BlockFeedbackRequest(
      exerciseText = dummyExerciseText,
      studentCodePython = exprFromPython("print('hi')"),
      submissionNr = 1,
      config = BlockFeedbackConfig.default.copy(enableUnitTests = true)
    )

    val plan = BlockFeedbackTestPlan(
      visibleTests = Seq(BlockFeedbackPythonTest("vis", "assert True", hint = Some("H"))),
      hiddenTests = Nil,
      fixtures = Nil,
      packages = Nil,
      timeoutMs = 1000,
      derivedHints = Nil
    )

    val runner: PythonRunRequest => Future[PythonRunResult] = (_: PythonRunRequest) =>
      Future.successful(
        PythonRunResult(
          status = PythonRunStatus.Failed,
          tests = Seq(
            RuntimeTestResult(
              name = "vis",
              status = PythonTestStatus.Failed,
              isHidden = false,
              message = Some("assertion failed"),
              durationMs = 1.0,
              hint = Some("H")
            )
          ),
          stdout = "",
          stderr = "",
          error = None,
          score = 0.0
        )
      )

    BlockFeedbackTestRunner
      .executeWithRunner(request, plan, runner)
      .map { outcome =>
        assertEquals(outcome.tests.size, 1)
        val t = outcome.tests.head
        assertEquals(t.name, "vis")
        assertEquals(t.passed, false)
        assert(t.expected.nonEmpty)
        assert(t.actual.nonEmpty)
        // message prefers hint (if present) or runtime message
        assertEquals(t.message, Some("H"))
      }
  }

  test("executeWithRunner recovers on runner failure and returns a runtime error outcome") {
    val request = BlockFeedbackRequest(
      exerciseText = dummyExerciseText,
      studentCodePython = exprFromPython("print('hi')"),
      submissionNr = 1,
      config = BlockFeedbackConfig.default.copy(enableUnitTests = true)
    )

    val plan = BlockFeedbackTestPlan(
      visibleTests = Nil,
      hiddenTests = Nil,
      fixtures = Nil,
      packages = Nil,
      timeoutMs = 1000,
      derivedHints = Nil
    )

    val runner: PythonRunRequest => Future[PythonRunResult] = (_: PythonRunRequest) =>
      Future.failed(new RuntimeException("boom"))

    BlockFeedbackTestRunner
      .executeWithRunner(request, plan, runner)
      .map { outcome =>
        assertEquals(outcome.runStatus, Some(PythonRunStatus.RuntimeError))
        assertEquals(outcome.normalizedScore, Some(0.0))
        assert(outcome.runtimeError.nonEmpty)
        assert(outcome.tests.nonEmpty)
        assertEquals(outcome.tests.head.passed, false)
      }
  }

  test("executeWithRunner does not block the caller (returns an incomplete Future)") {
    val request = BlockFeedbackRequest(
      exerciseText = dummyExerciseText,
      studentCodePython = exprFromPython("print('hi')"),
      submissionNr = 1,
      config = BlockFeedbackConfig.default.copy(enableUnitTests = true)
    )

    val plan = BlockFeedbackTestPlan(
      visibleTests = Seq(BlockFeedbackPythonTest("t", "assert True")),
      hiddenTests = Nil,
      fixtures = Nil,
      packages = Nil,
      timeoutMs = 1000,
      derivedHints = Nil
    )

    val gate = Promise[PythonRunResult]()
    val runner: PythonRunRequest => Future[PythonRunResult] = (_: PythonRunRequest) => gate.future

    val f = BlockFeedbackTestRunner.executeWithRunner(request, plan, runner)

    var progressed = false
    progressed = true
    assert(progressed)

    gate.success(
      PythonRunResult(
        status = PythonRunStatus.Success,
        tests = Nil,
        stdout = "",
        stderr = "",
        error = None,
        score = 1.0
      )
    )

    f.map(outcome => assertEquals(outcome.runStatus, Some(PythonRunStatus.Success)))
  }
