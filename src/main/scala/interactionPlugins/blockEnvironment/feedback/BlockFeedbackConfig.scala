package interactionPlugins.blockEnvironment.feedback

import interactionPlugins.pythonExercises.{PythonFixture, PythonUnitTest}

/**
 * Configuration of the feedback pipeline for a specific exercise/submission.
 * Can be customized or overridden per exercise later on.
 */
final case class BlockFeedbackConfig(
  enableVmStaticChecks: Boolean = true,
  enablePythonStaticChecks: Boolean = true,
  enableUnitTests: Boolean = false,
  enableAiSummary: Boolean = false,
  visibleTests: Seq[PythonUnitTest] = Nil,
  hiddenTests: Seq[PythonUnitTest] = Nil,
  fixtures: Seq[PythonFixture] = Nil,
  packages: Seq[String] = Nil,
  timeoutMs: Int = 5000
)

object BlockFeedbackConfig {
  val default: BlockFeedbackConfig = BlockFeedbackConfig()
}
