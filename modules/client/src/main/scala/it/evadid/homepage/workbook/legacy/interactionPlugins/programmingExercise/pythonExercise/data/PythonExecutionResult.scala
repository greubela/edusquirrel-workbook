package it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.data

import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.data.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.pyodide.*
import PythonExecutionResult.*

case class PythonExecutionResult(
                                  request: PythonExecutionRequest,
                                  state: PythonExecutionState
                                ) derives upickle.default.ReadWriter {

}

object PythonExecutionResult {

  enum PythonExecutionRunningState derives upickle.default.ReadWriter {
    case RUNNING, FINISHED_ERROR, FINISHED_LINE_LIMIT, FINISHED_SUCCESS
  }

  case class PythonExecutionState(
                                   stdout: String,
                                   stderr: String,
                                   globals: Map[String, String],
                                   locals: Map[String, String],
                                   linesExecuted: Int,
                                   runningState: PythonExecutionRunningState
                                 ) derives upickle.default.ReadWriter

}

