package it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.data

case class PythonExecutionRequest(
                                   pythonCode: String,
                                   maxLinesToExecute: Option[Int]
                                 ) {

}

object PythonExecutionRequest {


}