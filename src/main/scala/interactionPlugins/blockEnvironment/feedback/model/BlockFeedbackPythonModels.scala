package interactionPlugins.blockEnvironment.feedback

/**
 * Feedback-owned representation of a Python test case.
 *
 * Intentionally decoupled from interactionPlugins.pythonExercises.* so the
 * feedback module can evolve independently. Conversion into the runtime model
 * happens at the execution boundary.
 */
final case class BlockFeedbackPythonTest(
    name: String,
    code: String,
    weight: Double = 1.0,
    hint: Option[String] = None
)

/** Feedback-owned representation of a Python fixture file. */
final case class BlockFeedbackPythonFixture(
    path: String,
    content: String,
    isBinary: Boolean = false
)
