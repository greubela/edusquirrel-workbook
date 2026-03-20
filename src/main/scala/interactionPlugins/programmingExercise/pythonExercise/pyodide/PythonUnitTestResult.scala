package interactionPlugins.programmingExercise.pythonExercise.pyodide

final case class PythonUnitTestResult(
    test: PythonUnitTest,
    execution: ExecutionResult,
    success: Boolean,
    testRunnerOutput: String,
    failures: List[String],
    errors: List[String]
)
