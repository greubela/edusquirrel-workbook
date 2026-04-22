package interactionPlugins.blockEnvironment.feedback.runtime

final case class PythonRunRequest(
  code: String,
  visibleTests: Seq[PythonUnitTest],
  hiddenTests: Seq[PythonUnitTest],
  fixtures: Seq[PythonFixture],
  packages: Seq[String],
  timeoutMs: Int
)

final case class PythonUnitTest(
  name: String,
  code: String,
  weight: Double = 1.0,
  hint: Option[String] = None
)

final case class PythonFixture(
  path: String,
  content: String,
  isBinary: Boolean = false
)

enum PythonRunStatus {
  case Success, Failed, RuntimeError
}

enum PythonTestStatus {
  case Passed, Failed, Errored
}

final case class PythonTestResult(
  name: String,
  status: PythonTestStatus,
  isHidden: Boolean,
  message: Option[String],
  durationMs: Double,
  hint: Option[String]
)

final case class PythonRunResult(
  status: PythonRunStatus,
  tests: Seq[PythonTestResult],
  stdout: String,
  stderr: String,
  error: Option[String],
  score: Double
)
