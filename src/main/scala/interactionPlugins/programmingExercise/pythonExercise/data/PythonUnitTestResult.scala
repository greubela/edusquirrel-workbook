package interactionPlugins.programmingExercise.pythonExercise.data

import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonExecutionResult.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonUnitTestResult.*

final case class PythonUnitTestResult(
                                       userCode: PythonExecutionRequest,
                                       tests: Set[PythonUnitTestGradingResult],
                                     ) {

}

object PythonUnitTestResult {

  enum GradingStatus {
    case UNFINISHED, SUCCESS, FAILED
  }
  
  case class PythonUnitTestGradingResult(
                                          test: PythonUnitTest,
                                          result: PythonExecutionResult,
                                          gradingStatus: GradingStatus
                                        ){
    
  }

  

}

