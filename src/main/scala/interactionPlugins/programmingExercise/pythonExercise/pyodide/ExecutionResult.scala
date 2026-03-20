package interactionPlugins.programmingExercise.pythonExercise.pyodide

final case class ExecutionResult(
    code: String,
    success: Boolean,
    stdout: String,
    stderr: String,
    globals: Map[String, String],
    locals: Map[String, String],
    exception: Option[String],
    linesExecuted: Int,
    maxExecutedLines: Option[Int],
    lineLimitHit: Boolean
)
