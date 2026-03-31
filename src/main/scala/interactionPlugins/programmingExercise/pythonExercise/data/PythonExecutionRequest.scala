package interactionPlugins.programmingExercise.pythonExercise.data

case class PythonExecutionRequest(
                                   pythonCode: String,
                                   maxLinesToExecute: Option[Int],
                                   includeSnapshots: Boolean = false
                                 ) {

}

object PythonExecutionRequest {


}
