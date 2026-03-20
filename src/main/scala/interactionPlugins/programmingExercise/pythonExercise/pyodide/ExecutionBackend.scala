package interactionPlugins.programmingExercise.pythonExercise.pyodide

sealed trait ExecutionBackend derives CanEqual

object ExecutionBackend {
  case object MainThread extends ExecutionBackend
  case object Worker extends ExecutionBackend
}
