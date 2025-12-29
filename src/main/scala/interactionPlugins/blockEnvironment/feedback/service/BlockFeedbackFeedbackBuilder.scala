package interactionPlugins.blockEnvironment.feedback

import interactionPlugins.pythonExercises.PythonRunStatus
import workbook.model.feedback.FeedbackStatus

/**
 * Builds the final UI feedback from runtime outcomes and the derived test plan.
 */
object BlockFeedbackFeedbackBuilder:

  def buildFeedback(
    request: BlockFeedbackRequest,
    plan: BlockFeedbackTestPlan,
    outcome: PythonRuntimeOutcome
  ): UltrichsNewCoolFeedback =
    val rawPython = request.pythonSource
    val hints = collectGeneralHints(outcome.tests, outcome.runtimeError, plan.derivedHints)
    val (normalizedScore, status) =
      computeScoreAndStatus(
        rawPython,
        outcome.tests,
        outcome.normalizedScore,
        outcome.runStatus,
        request.config.enableUnitTests
      )
    val summary = buildSummary(outcome.tests, normalizedScore)

    UltrichsNewCoolFeedback(
      summary = summary,
      tests = outcome.tests,
      generalHints = hints,
      rawPython = rawPython,
      status = status,
      normalizedScore = normalizedScore
    )

  private def collectGeneralHints(
    tests: Seq[PythonTestResult],
    runtimeError: Option[String],
    planHints: Seq[String]
  ): Seq[String] =
    val runtimeHints = runtimeError.toSeq ++ tests.filterNot(_.passed).flatMap(_.message)
    (planHints ++ runtimeHints).distinct

  private def computeScoreAndStatus(
    rawPython: String,
    tests: Seq[PythonTestResult],
    runtimeScore: Option[Double],
    runtimeStatus: Option[PythonRunStatus],
    testsEnabled: Boolean
  ): (Double, FeedbackStatus) =
    val trimmed = rawPython.trim
    if trimmed.isEmpty then
      (0.0, FeedbackStatus.NOT_STARTET)
    else if !testsEnabled then
      (1.0, FeedbackStatus.IN_PROGRESS)
    else
      val score = runtimeScore.getOrElse(
        if tests.isEmpty then 1.0
        else math.max(0.0, math.min(1.0, tests.count(_.passed).toDouble / tests.size))
      )
      val status = runtimeStatus match
        case Some(PythonRunStatus.Success) if score >= 1.0 => FeedbackStatus.FINISHED
        case Some(PythonRunStatus.RuntimeError)            => FeedbackStatus.IN_PROGRESS
        case Some(PythonRunStatus.Success)                 => FeedbackStatus.IN_PROGRESS
        case Some(PythonRunStatus.Failed)                  => FeedbackStatus.IN_PROGRESS
        case None if tests.isEmpty                         => FeedbackStatus.IN_PROGRESS
        case None if tests.exists(!_.passed)               => FeedbackStatus.IN_PROGRESS
        case None                                          => FeedbackStatus.FINISHED
      (score, status)

  private def buildSummary(
    tests: Seq[PythonTestResult],
    normalizedScore: Double
  ): String =
    if tests.isEmpty then
      "No tests were executed yet."
    else
      val total = tests.size
      val passed = tests.count(_.passed)
      val percent = f"${normalizedScore * 100}%.1f%%"
      s"Passed $passed of $total tests ($percent)."
