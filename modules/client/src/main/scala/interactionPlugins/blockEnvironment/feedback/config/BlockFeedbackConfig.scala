package interactionPlugins.blockEnvironment.feedback

import interactionPlugins.blockEnvironment.feedback.ml.RouterMode

/**
 * Configuration of the feedback pipeline for a specific exercise/submission.
 * Can be customized or overridden per exercise later on.
 */
final case class BlockFeedbackConfig(
  enableVmStaticChecks: Boolean = true,
  enablePythonStaticChecks: Boolean = true,
  enableUnitTests: Boolean = false,
  enableAiSummary: Boolean = false,
  visibleTests: Seq[BlockFeedbackPythonTest] = Nil,
  hiddenTests: Seq[BlockFeedbackPythonTest] = Nil,
  fixtures: Seq[BlockFeedbackPythonFixture] = Nil,
  packages: Seq[String] = Nil,
  timeoutMs: Int = 5000,

  /** If true, executes tests in separate runs to avoid shared state. */
  isolatePerTest: Boolean = false,

  /** If false, hidden tests are never executed. */
  runHiddenTests: Boolean = true,

  /** If true, run hidden tests only if all visible tests pass. */
  runHiddenOnlyIfVisiblePass: Boolean = true,

  /** Routing mode for choosing hint templates (heuristics vs. mini-ML). */
  routerMode: RouterMode = RouterMode.Heuristic,

  /** If enabled, logs features + weak labels for offline training. */
  enableMlLogging: Boolean = false,

  /** Optional URL of a JSON softmax model served as a static file. */
  mlModelUrl: Option[String] = None,

  /** Optional HTTP endpoint that receives training examples as JSON. */
  mlLogUrl: Option[String] = None,

  isScriptExercise: Boolean = false
)

object BlockFeedbackConfig {
  // Per-exercise configs can still override/disable by setting explicit values.
  val default: BlockFeedbackConfig =
    BlockFeedbackConfig(
      enableAiSummary = true,
      routerMode = RouterMode.Ml,
      enableMlLogging = true,
      mlLogUrl = Some("http://127.0.0.1:8000/api/ml/log-example"),
      mlModelUrl = Some("http://127.0.0.1:8000/api/ml/model")
    )
}
