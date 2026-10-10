package it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.data

import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.data.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.pyodide.*
import PythonExecutionResult.*
import PythonUnitTestResult.*

final case class PythonUnitTestResult(
                                       userCode: PythonExecutionRequest,
                                       tests: Set[PythonUnitTestGradingResult],
                                     ) derives upickle.default.ReadWriter {

}

object PythonUnitTestResult {

  enum GradingStatus derives upickle.default.ReadWriter {
    case UNFINISHED, SUCCESS, FAILED
  }
  
  case class PythonUnitTestGradingResult(
                                          test: PythonUnitTest,
                                          result: PythonExecutionResult,
                                          gradingStatus: GradingStatus
                                        ) derives upickle.default.ReadWriter{
    
  }

  

}

