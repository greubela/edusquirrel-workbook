package interactionPlugins.programmingExercise.pythonExercise.data

import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonExecutionResult.*

case class PythonExecutionResult(
                                  request: PythonExecutionRequest,
                                  state: PythonExecutionState
                                ) {

}

object PythonExecutionResult {

  enum PythonExecutionRunningState {
    case RUNNING, FINISHED_ERROR, FINISHED_LINE_LIMIT, FINISHED_SUCCESS
  }

  case class PythonExecutionState(
                                   stdout: String,
                                   stderr: String,
                                   globals: Map[String, String],
                                   locals: Map[String, String],
                                   linesExecuted: Int,
                                   runningState: PythonExecutionRunningState
                                 )

}

