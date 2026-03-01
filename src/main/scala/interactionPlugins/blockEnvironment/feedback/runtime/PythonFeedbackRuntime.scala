package interactionPlugins.blockEnvironment.feedback.runtime

import interactionPlugins.pythonExercises.{
  PythonRunRequest,
  PythonRunResult,
  PythonRuntimeService,
  PythonRunStatus,
  PythonTestResult,
  PythonTestStatus
}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}
import java.util.concurrent.TimeoutException

/**
 * Feedback-facing runtime wrapper that adds timeout, optional per-test isolation,
 * and shields the feedback pipeline from the underlying runtime details.
 */
object PythonFeedbackRuntime:

  private[feedback] type Runner = PythonRunRequest => Future[PythonRunResult]

  private val defaultRunner: Runner = (request: PythonRunRequest) => PythonRuntimeService.run(request)

  /**
   * Execute the given Python run request with optional per-test isolation and timeout guard.
   * Per-test isolation re-executes user code for each test to avoid shared state between tests.
   */
  def run(
    request: PythonRunRequest,
    isolatePerTest: Boolean = true
  )(using ExecutionContext): Future[PythonRunResult] =
    runWith(request, isolatePerTest, defaultRunner)

  /** Test hook: same as [[run]], but with an injected runner function. */
  private[feedback] def runWith(
    request: PythonRunRequest,
    isolatePerTest: Boolean,
    runner: Runner
  )(using ExecutionContext): Future[PythonRunResult] =
    // When Pyodide hasn't loaded yet (cold start) we must allow far more time
    // than the user-configured execution timeout.  Once Pyodide is warm the
    // short timeout is used both to bound execution and to kill infinite loops.
    // The cold-start timeout (120 s) is only active until the first successful
    // "ready" signal, so it is not a user-visible regression for repeat submits.
    val effectiveTimeoutMs: Int =
      if (runner eq defaultRunner) && !PythonRuntimeService.isReady then
        math.max(request.timeoutMs, 120000)
      else
        request.timeoutMs
    val base = if isolatePerTest then runIsolatedPerTest(request, runner) else runner(request)
    withTimeout(base, effectiveTimeoutMs)

  private def runIsolatedPerTest(request: PythonRunRequest, runner: Runner)(using ExecutionContext): Future[PythonRunResult] =
    val allTests =
      request.visibleTests.map(test => (test, false)) ++
        request.hiddenTests.map(test => (test, true))

    val totalWeight = allTests.map(_._1.weight).sum match
      case w if w <= 0 => 1.0
      case w           => w

    val initial: Future[(Seq[PythonTestResult], Double, Boolean, Seq[String], Seq[String], Option[String])] =
      Future.successful((Seq.empty, 0.0, false, Seq.empty, Seq.empty, None))

    val aggregated = allTests.foldLeft(initial) { case (accF, (test, isHidden)) =>
      accF.flatMap { case (testsAcc, earnedAcc, hadRuntimeError, outStdout, outStderr, firstError) =>
        val singleRequest = request.copy(
          visibleTests = if isHidden then Nil else Seq(test),
          hiddenTests = if isHidden then Seq(test) else Nil
        )

        runner(singleRequest).map { result =>
          val testResult = result.tests.headOption.getOrElse(
            PythonTestResult(
              name = test.name,
              status = PythonTestStatus.Errored,
              isHidden = isHidden,
              message = result.error.orElse(Some("Test did not return a result.")),
              durationMs = 0.0,
              hint = test.hint
            )
          )

          val earned = if testResult.status == PythonTestStatus.Passed then test.weight else 0.0
          val runtimeErr = hadRuntimeError || result.status == PythonRunStatus.RuntimeError
          val errMsg = firstError.orElse(result.error)

          (
            testsAcc :+ testResult,
            earnedAcc + earned,
            runtimeErr,
            outStdout :+ result.stdout,
            outStderr :+ result.stderr,
            errMsg
          )
        }
      }
    }

    aggregated.map { case (tests, earned, hadRuntimeError, stdouts, stderrs, firstError) =>
      val score = math.max(0.0, math.min(1.0, earned / totalWeight))
      val status =
        if hadRuntimeError then PythonRunStatus.RuntimeError
        else if tests.exists(_.status == PythonTestStatus.Failed) then PythonRunStatus.Failed
        else PythonRunStatus.Success

      PythonRunResult(
        status = status,
        tests = tests,
        stdout = stdouts.filter(_.nonEmpty).mkString("\n"),
        stderr = stderrs.filter(_.nonEmpty).mkString("\n"),
        error = firstError,
        score = score
      )
    }.recover { case ex =>
      PythonRunResult(
        status = PythonRunStatus.RuntimeError,
        tests = Seq.empty,
        stdout = "",
        stderr = "",
        error = Option(ex.getMessage).filter(_.nonEmpty),
        score = 0.0
      )
    }

  private def withTimeout[A](future: Future[A], timeoutMs: Int)(using ExecutionContext): Future[A] =
    if timeoutMs <= 0 then future
    else
      val p = Promise[A]()
      val handle: SetTimeoutHandle = setTimeout(timeoutMs.toDouble) {
        // Terminate the worker so an infinite-loop Python script is actually
        // killed (not just ignored).  The next run() call creates a fresh worker.
        PythonRuntimeService.terminateWorker()
        p.tryFailure(new TimeoutException(s"Python runtime timed out after ${timeoutMs}ms"))
      }
      future.onComplete { result =>
        clearTimeout(handle)
        p.tryComplete(result)
      }
      p.future
