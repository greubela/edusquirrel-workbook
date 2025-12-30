package interactionPlugins.blockEnvironment.feedback

import interactionPlugins.pythonExercises.PythonUnitTest

/**
 * Example configurations for specific block/Python exercises.
 *
 * These objects are typically invoked later by exercise definitions or the grader
 * and injected into BlockFeedbackRequest.config.
 */
object BlockExerciseConfigs {

  /**
   * Example: exercise "add two numbers".
   *
   * Expected student solution (abstract):
   *   def add(a: int, b: int) -> int:
   *       ...
   */
  val addTwoNumbers: BlockFeedbackConfig =
    BlockFeedbackConfig(
      enableVmStaticChecks = true,
      enablePythonStaticChecks = true,
      enableUnitTests = true,
      enableAiSummary = false,
      visibleTests = Seq(
        PythonUnitTest(
          name = "add_small_positive",
          code = "assert add(1, 2) == 3",
          hint = Some("Teste zuerst einfache positive Zahlen.")
        ),
        PythonUnitTest(
          name = "add_zero",
          code = "assert add(0, 5) == 5"
        )
      ),
      hiddenTests = Seq(
        PythonUnitTest(
          name = "add_negative",
          code = "assert add(-3, 7) == 4",
          weight = 2.0,
          hint = Some("Achte auch auf negative Zahlen.")
        )
      ),
      fixtures = Nil,
      packages = Nil,
      timeoutMs = 4000
    )

  /**
   * Example: exercise "maximum value in a list".
   *
   * Expected student solution (abstract):
   *   def max_in_list(xs: list[int]) -> int:
   *       ...
   */
  val maxInList: BlockFeedbackConfig =
    BlockFeedbackConfig(
      enableVmStaticChecks = true,
      enablePythonStaticChecks = true,
      enableUnitTests = true,
      enableAiSummary = false,
      visibleTests = Seq(
        PythonUnitTest(
          name = "simple_list",
          code = "assert max_in_list([1, 2, 3]) == 3"
        ),
        PythonUnitTest(
          name = "unordered",
          code = "assert max_in_list([5, 1, 9, 2]) == 9"
        )
      ),
      hiddenTests = Seq(
        PythonUnitTest(
          name = "with_negative",
          code = "assert max_in_list([-5, -1, -9]) == -1",
          weight = 2.0
        )
      ),
      fixtures = Nil,
      packages = Nil,
      timeoutMs = 4000
    )
}
