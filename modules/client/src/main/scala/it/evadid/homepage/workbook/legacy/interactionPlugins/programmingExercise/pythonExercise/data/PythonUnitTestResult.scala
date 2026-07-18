package it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.data

import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.data.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.pyodide.*
import PythonExecutionResult.*
import PythonUnitTestResult.*

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

