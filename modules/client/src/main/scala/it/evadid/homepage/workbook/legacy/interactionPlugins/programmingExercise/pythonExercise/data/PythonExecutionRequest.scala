package it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.data

case class PythonExecutionRequest(
                                   pythonCode: String,
                                   maxLinesToExecute: Option[Int]
                                 ) derives upickle.default.ReadWriter {

}

object PythonExecutionRequest {


}