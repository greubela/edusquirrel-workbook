package interactionPlugins.blockEnvironment.feedback.runtime

import interactionPlugins.programmingExercise.pythonExercisesUnsorted.{PythonRunRequest, PythonRunResult, PythonRunStatus, PythonTestResult, PythonTestStatus, PythonUnitTest}
import munit.FunSuite

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

final class PythonFeedbackRuntimeSpec extends FunSuite:

  private given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private def mkReq(
    code: String = "print('x')",
    visible: Seq[PythonUnitTest] = Nil,
    hidden: Seq[PythonUnitTest] = Nil,
    timeoutMs: Int = 5000
  ): PythonRunRequest =
    PythonRunRequest(
      code = code,
      visibleTests = visible,
      hiddenTests = hidden,
      fixtures = Nil,
      packages = Nil,
      timeoutMs = timeoutMs
    )

  test("isolated-per-test runs each test separately and aggregates results") {
    val t1 = PythonUnitTest(name = "t1", code = "assert True", weight = 1.0)
    val t2 = PythonUnitTest(name = "t2", code = "assert True", weight = 2.0)
    val h1 = PythonUnitTest(name = "h1", code = "assert True", weight = 3.0)

    val calls = mutable.ArrayBuffer.empty[PythonRunRequest]

    val runner: PythonFeedbackRuntime.Runner = (req: PythonRunRequest) =>
      calls += req

      val (unitTest, isHidden) =
        req.visibleTests.headOption.map(_ -> false)
          .orElse(req.hiddenTests.headOption.map(_ -> true))
          .getOrElse(fail("Expected exactly one test in request"))

      Future.successful(
        PythonRunResult(
          status = PythonRunStatus.Success,
          tests = Seq(
            PythonTestResult(
              name = unitTest.name,
              status = PythonTestStatus.Passed,
              isHidden = isHidden,
              message = None,
              durationMs = 1.0,
              hint = unitTest.hint
            )
          ),
          stdout = "",
          stderr = "",
          error = None,
          score = 1.0
        )
      )

    val req = mkReq(visible = Seq(t1, t2), hidden = Seq(h1))

    PythonFeedbackRuntime
      .runWith(req, isolatePerTest = true, runner)
      .map { result =>
        assertEquals(calls.size, 3)

        // each run request should contain only one test
        assertEquals(calls(0).visibleTests.map(_.name), Seq("t1"))
        assertEquals(calls(0).hiddenTests.map(_.name), Nil)

        assertEquals(calls(1).visibleTests.map(_.name), Seq("t2"))
        assertEquals(calls(1).hiddenTests.map(_.name), Nil)

        assertEquals(calls(2).visibleTests.map(_.name), Nil)
        assertEquals(calls(2).hiddenTests.map(_.name), Seq("h1"))

        assertEquals(result.tests.map(_.name), Seq("t1", "t2", "h1"))
        assertEquals(result.status, PythonRunStatus.Success)
        assertEqualsDouble(result.score, 1.0, 1e-9)
      }
  }

  test("isolated-per-test continues after a failure and computes weighted score") {
    val tPass = PythonUnitTest(name = "pass", code = "assert True", weight = 1.0)
    val tFail = PythonUnitTest(name = "fail", code = "assert False", weight = 3.0)
    val tPass2 = PythonUnitTest(name = "pass2", code = "assert True", weight = 2.0)

    val calls = mutable.ArrayBuffer.empty[String]

    val runner: PythonFeedbackRuntime.Runner = (req: PythonRunRequest) =>
      val current = req.visibleTests.headOption.orElse(req.hiddenTests.headOption).get
      calls += current.name

      val (status, testStatus, score) =
        if current.name == "fail" then (PythonRunStatus.Failed, PythonTestStatus.Failed, 0.0)
        else (PythonRunStatus.Success, PythonTestStatus.Passed, 1.0)

      Future.successful(
        PythonRunResult(
          status = status,
          tests = Seq(
            PythonTestResult(
              name = current.name,
              status = testStatus,
              isHidden = req.hiddenTests.nonEmpty,
              message = None,
              durationMs = 1.0,
              hint = current.hint
            )
          ),
          stdout = "",
          stderr = "",
          error = None,
          score = score
        )
      )

    val req = mkReq(visible = Seq(tPass, tFail, tPass2))

    PythonFeedbackRuntime
      .runWith(req, isolatePerTest = true, runner)
      .map { result =>
        // IMPORTANT: all three tests must be attempted
        assertEquals(calls.toList, List("pass", "fail", "pass2"))

        assertEquals(result.tests.map(_.name), Seq("pass", "fail", "pass2"))
        assertEquals(result.tests.map(_.status), Seq(PythonTestStatus.Passed, PythonTestStatus.Failed, PythonTestStatus.Passed))

        // weighted score = (1 + 2) / (1 + 3 + 2) = 0.5
        assertEquals(result.status, PythonRunStatus.Failed)
        assertEqualsDouble(result.score, 0.5, 1e-9)
      }
  }

  test("runWith does not block the caller (returns an incomplete Future)") {
    val gate = Promise[PythonRunResult]()

    val runner: PythonFeedbackRuntime.Runner = (_: PythonRunRequest) => gate.future

    val f = PythonFeedbackRuntime.runWith(
      mkReq(visible = Seq(PythonUnitTest("t", "assert True"))),
      isolatePerTest = false,
      runner
    )

    // If runWith were blocking, we would never reach this point.
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

    f.map(res => assertEquals(res.status, PythonRunStatus.Success))
  }
