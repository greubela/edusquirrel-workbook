package interactionPlugins.programmingExercise.pythonExercise.pyodide

import scala.scalajs.js

import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.*

object PyodideBackends {

  case class CallbackLibrary(moduleName: String, methodMap: Map[String, Vector[js.Any] => Unit])
  
  final case class JsDataVariable(
      varName: String,
      jsTypeOf: String,
      stringRepresentation: String
  )

  trait SyncModuleBackend {
    def moduleName: String

    def handleModuleCall(callbackName: String, args: Seq[JsDataVariable]): js.Any
  }

  final case class CallbackOp(
                               module: String,
                               method: String,
                               args: Vector[js.Any]
                             )

  final case class PythonRunConfig(
                                    context: js.Dictionary[js.Any] = js.Dictionary.empty,
                                    resetGlobals: Boolean = false,
                                    captureStdout: Boolean = true,
                                    captureStderr: Boolean = true
                                  )

  final case class PythonRunReport(
                                    callbackOps: Vector[CallbackOp],
                                    stdout: String,
                                    stderr: String
                                  )

  final case class PythonWorkerFailure(
                                        message: String,
                                        stdout: String,
                                        stderr: String
                                      ) extends RuntimeException(message)
  
}
