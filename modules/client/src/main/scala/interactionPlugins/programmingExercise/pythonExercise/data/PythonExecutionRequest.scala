package interactionPlugins.programmingExercise.pythonExercise.data

case class PythonExecutionRequest(
                                   pythonCode: String,
                                   maxLinesToExecute: Option[Int]
                                 ) {

}

object PythonExecutionRequest {


}