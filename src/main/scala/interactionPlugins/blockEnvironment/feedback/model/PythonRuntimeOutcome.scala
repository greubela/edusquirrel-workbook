package interactionPlugins.blockEnvironment.feedback

import interactionPlugins.blockEnvironment.feedback.runtime.PythonRunStatus

/**
 * Intermediate structure that groups the information returned from the Python runtime.
 */
final case class PythonRuntimeOutcome(
  tests: Seq[PythonTestResult],
  runStatus: Option[PythonRunStatus],
  normalizedScore: Option[Double],
  runtimeError: Option[String],
  stdout: Option[String],
  stderr: Option[String]
)

object PythonRuntimeOutcome:
  val empty: PythonRuntimeOutcome = PythonRuntimeOutcome(Seq.empty, None, None, None, None, None)
