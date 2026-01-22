package interactionPlugins.blockEnvironment.feedback

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
  runHiddenOnlyIfVisiblePass: Boolean = true
)

object BlockFeedbackConfig {
  val default: BlockFeedbackConfig = BlockFeedbackConfig()
}
