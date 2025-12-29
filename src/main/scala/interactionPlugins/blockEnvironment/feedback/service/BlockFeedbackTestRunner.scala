package interactionPlugins.blockEnvironment.feedback

import interactionPlugins.blockEnvironment.feedback.runtime.PythonFeedbackRuntime
import interactionPlugins.pythonExercises.{
  PythonRunRequest,
  PythonRunStatus,
  PythonTestResult => RuntimeTestResult,
  PythonTestStatus
}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * Executes the Python unit tests that belong to a derived test plan.
 */
object BlockFeedbackTestRunner:

  def execute(
    request: BlockFeedbackRequest,
    plan: BlockFeedbackTestPlan
  )(using ExecutionContext): Future[PythonRuntimeOutcome] =
    executeWithRunner(request, plan, req => PythonFeedbackRuntime.run(req))

  /** Test hook: execute with an injected runtime function (no real Python runtime needed). */
  private[feedback] def executeWithRunner(
    request: BlockFeedbackRequest,
    plan: BlockFeedbackTestPlan,
    runPython: PythonRunRequest => Future[interactionPlugins.pythonExercises.PythonRunResult]
  )(using ExecutionContext): Future[PythonRuntimeOutcome] =
    val rawPython = request.pythonSource
    val pythonRequest = PythonRunRequest(
      code = rawPython,
      visibleTests = plan.visibleTests,
      hiddenTests = plan.hiddenTests,
      fixtures = plan.fixtures,
      packages = plan.packages,
      timeoutMs = plan.timeoutMs
    )

    runPython(pythonRequest).map { runResult =>
      val tests = runResult.tests.map(mapRuntimeTestResult)
      PythonRuntimeOutcome(
        tests = tests,
        runStatus = Some(runResult.status),
        normalizedScore = Some(runResult.score),
        runtimeError = runResult.error
      )
    }.recover { case NonFatal(error) =>
      runtimeFailureOutcome(error)
    }

  private def runtimeFailureOutcome(error: Throwable): PythonRuntimeOutcome =
    val fallbackMessage = Option(error.getMessage).getOrElse("Python runtime failure")
    PythonRuntimeOutcome(
      tests = Seq(
        PythonTestResult(
          name = "python-runtime",
          passed = false,
          expected = "Python runtime must start",
          actual = s"Runtime error: $fallbackMessage",
          message = Some(s"Failed to execute tests: $fallbackMessage")
        )
      ),
      runStatus = Some(PythonRunStatus.RuntimeError),
      normalizedScore = Some(0.0),
      runtimeError = Some(fallbackMessage)
    )

  private def mapRuntimeTestResult(entry: RuntimeTestResult): PythonTestResult =
    val passed = entry.status == PythonTestStatus.Passed
    val actual = entry.status match
      case PythonTestStatus.Passed  => "OK"
      case PythonTestStatus.Failed  => entry.message.getOrElse("Assertion failed")
      case PythonTestStatus.Errored => entry.message.getOrElse("Runtime error")
    PythonTestResult(
      name = entry.name,
      passed = passed,
      expected = entry.hint.getOrElse("Test should pass"),
      actual = actual,
      message = entry.hint.orElse(entry.message)
    )
