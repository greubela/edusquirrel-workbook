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
    executeWithRuntime(
      request,
      plan,
      (req, isolatePerTest) => PythonFeedbackRuntime.run(req, isolatePerTest = isolatePerTest)
    )

  /** Test hook: execute with an injected runtime function (no real Python runtime needed). */
  private[feedback] def executeWithRunner(
    request: BlockFeedbackRequest,
    plan: BlockFeedbackTestPlan,
    runPython: PythonRunRequest => Future[interactionPlugins.pythonExercises.PythonRunResult]
  )(using ExecutionContext): Future[PythonRuntimeOutcome] =
    executeWithRuntime(request, plan, (req, _) => runPython(req))

  /**
   * Test hook: same behavior as [[execute]], but with an injected runtime function.
   * This makes it possible to assert whether hidden tests are skipped and whether
   * the isolatePerTest flag is forwarded.
   */
  private[feedback] def executeWithRuntime(
    request: BlockFeedbackRequest,
    plan: BlockFeedbackTestPlan,
    runPython: (PythonRunRequest, Boolean) => Future[interactionPlugins.pythonExercises.PythonRunResult]
  )(using ExecutionContext): Future[PythonRuntimeOutcome] =
    val rawPython = request.pythonSource
    val runtimeFixtures = plan.fixtures.map(toRuntimeFixture)

    def mkRequest(
      visible: Seq[BlockFeedbackPythonTest],
      hidden: Seq[BlockFeedbackPythonTest]
    ): PythonRunRequest =
      PythonRunRequest(
        code = rawPython,
        visibleTests = visible.map(t => toRuntimeTest(t, request.humanLanguage)),
        hiddenTests = hidden.map(t => toRuntimeTest(t, request.humanLanguage)),
        fixtures = runtimeFixtures,
        packages = plan.packages,
        timeoutMs = plan.timeoutMs
      )

    def allPassed(expected: Seq[BlockFeedbackPythonTest], results: Seq[RuntimeTestResult], hidden: Boolean): Boolean =
      expected.forall { t =>
        results
          .find(r => r.name == t.name && r.isHidden == hidden)
          .exists(_.status == PythonTestStatus.Passed)
      }

    def combineScore(
      visibleResults: Seq[RuntimeTestResult],
      hiddenResults: Seq[RuntimeTestResult],
      hiddenExecuted: Boolean
    ): Double =
      val weights: Map[(String, Boolean), Double] =
        (plan.visibleTests.map(t => (t.name, false) -> t.weight) ++
          (if hiddenExecuted then plan.hiddenTests.map(t => (t.name, true) -> t.weight) else Nil)).toMap

      val allResults =
        visibleResults ++ (if hiddenExecuted then hiddenResults else Nil)

      val total = weights.values.sum match
        case w if w <= 0 => 1.0
        case w           => w

      val passedKeys = allResults.collect { case r if r.status == PythonTestStatus.Passed => (r.name, r.isHidden) }.toSet
      val earned = weights.collect { case (k, w) if passedKeys.contains(k) => w }.sum
      math.max(0.0, math.min(1.0, earned / total))

    val isolatePerTest = request.config.isolatePerTest
    val visibleReq = mkRequest(visible = plan.visibleTests, hidden = Nil)
    val hiddenReq = mkRequest(visible = Nil, hidden = plan.hiddenTests)

    val visibleRunF = runPython(visibleReq, isolatePerTest)

    visibleRunF.flatMap { visibleRun =>
      val visibleAllPassed = allPassed(plan.visibleTests, visibleRun.tests, hidden = false)
      val shouldRunHidden =
        request.config.runHiddenTests &&
          (!request.config.runHiddenOnlyIfVisiblePass || visibleAllPassed)

      val runHidden = shouldRunHidden && plan.hiddenTests.nonEmpty
      val hiddenRunF: Future[Option[interactionPlugins.pythonExercises.PythonRunResult]] =
        if runHidden then runPython(hiddenReq, isolatePerTest).map(Some(_))
        else Future.successful(None)

      hiddenRunF.map {
        case Some(hiddenRun) =>
          val combinedTests = visibleRun.tests ++ hiddenRun.tests
          val combinedStatus =
            if visibleRun.status == PythonRunStatus.RuntimeError || hiddenRun.status == PythonRunStatus.RuntimeError then
              PythonRunStatus.RuntimeError
            else if combinedTests.exists(_.status != PythonTestStatus.Passed) then
              PythonRunStatus.Failed
            else
              PythonRunStatus.Success

          val combinedScore = combineScore(visibleRun.tests, hiddenRun.tests, hiddenExecuted = true)
          val combinedError = visibleRun.error.orElse(hiddenRun.error)
          val mapped = combinedTests.map(mapRuntimeTestResult)

          PythonRuntimeOutcome(
            tests = mapped,
            runStatus = Some(combinedStatus),
            normalizedScore = Some(combinedScore),
            runtimeError = combinedError,
            stdout = Some((visibleRun.stdout +: Seq(hiddenRun.stdout)).filter(_.nonEmpty).mkString("\n")).filter(_.nonEmpty),
            stderr = Some((visibleRun.stderr +: Seq(hiddenRun.stderr)).filter(_.nonEmpty).mkString("\n")).filter(_.nonEmpty)
          )

        case None =>
          val status =
            if visibleRun.status == PythonRunStatus.RuntimeError then PythonRunStatus.RuntimeError
            else if visibleRun.tests.exists(_.status != PythonTestStatus.Passed) then PythonRunStatus.Failed
            else PythonRunStatus.Success
          val score = combineScore(visibleRun.tests, Nil, hiddenExecuted = false)
          val mapped = visibleRun.tests.map(mapRuntimeTestResult)

          PythonRuntimeOutcome(
            tests = mapped,
            runStatus = Some(status),
            normalizedScore = Some(score),
            runtimeError = visibleRun.error,
            stdout = Option(visibleRun.stdout).filter(_.nonEmpty),
            stderr = Option(visibleRun.stderr).filter(_.nonEmpty)
          )
      }
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
      runtimeError = Some(fallbackMessage),
      stdout = None,
      stderr = None
    )

  private def mapRuntimeTestResult(entry: RuntimeTestResult): PythonTestResult =
    val passed = entry.status == PythonTestStatus.Passed

    def parseExpectedActual(msg: String): Option[(String, String)] = {
      val Pattern = "(?s)expected=\\s*(.+?)\\s+actual=\\s*(.+)".r
      msg match
        case Pattern(exp, act) => Some(exp.trim -> act.trim)
        case _ => None
    }

    val parsed = entry.message.flatMap(parseExpectedActual)
    val expected = parsed.map(_._1).getOrElse("Test should pass")
    val actual = parsed.map(_._2).getOrElse(
      entry.status match
        case PythonTestStatus.Passed  => "OK"
        case PythonTestStatus.Failed  => entry.message.getOrElse("Assertion failed")
        case PythonTestStatus.Errored => entry.message.getOrElse("Runtime error")
    )

    val message =
      if parsed.isDefined then entry.hint
      else entry.hint.orElse(entry.message)

    PythonTestResult(
      name = entry.name,
      passed = passed,
      expected = expected,
      actual = actual,
      message = message
    )

  private def toRuntimeTest(
      test: BlockFeedbackPythonTest,
      humanLanguage: contentmanagement.model.language.HumanLanguage
  ): interactionPlugins.pythonExercises.PythonUnitTest =
    interactionPlugins.pythonExercises.PythonUnitTest(
      name = test.name,
      code = test.code,
      weight = test.weight,
      hint = Some(buildHint(test, humanLanguage))
    )

  private def buildHint(
      test: BlockFeedbackPythonTest,
      humanLanguage: contentmanagement.model.language.HumanLanguage
  ): String =
    val code = Option(test.code).getOrElse("").trim
    val isGerman = humanLanguage == contentmanagement.model.language.AppLanguage.German

    val hintOpt = (if isGerman then test.hintDE.orElse(test.hint) else test.hint)
      .map(_.trim).filter(_.nonEmpty)

    val AssertEq = "(?i)^assert\\s+(.+?)\\s*==\\s*(.+)$".r
    val AssertIn = "(?i)^assert\\s+(.+?)\\s+in\\s+(.+)$".r
    val AssertRaw = "(?i)^assert\\s+(.+)$".r

    val base =
      code match
        case AssertEq(left, right) =>
          if isGerman then s"Prüft, dass $left gleich $right ist."
          else s"Checks that $left equals $right."
        case AssertIn(left, right) =>
          if isGerman then s"Prüft, dass $left in $right liegt."
          else s"Checks that $left is in $right."
        case AssertRaw(expr) =>
          if isGerman then s"Prüft, dass $expr wahr ist."
          else s"Checks that $expr is true."
        case _ =>
          if isGerman then "Prüft das erwartete Ergebnis für diesen Fall."
          else "Checks the expected result for this case."

    hintOpt match
      case Some(h) =>
        if h == base then base
        else if isGerman then s"$base Hinweis: $h"
        else s"$base Hint: $h"
      case _ => base

  private def toRuntimeFixture(
      fixture: BlockFeedbackPythonFixture
  ): interactionPlugins.pythonExercises.PythonFixture =
    interactionPlugins.pythonExercises.PythonFixture(
      path = fixture.path,
      content = fixture.content,
      isBinary = fixture.isBinary
    )
