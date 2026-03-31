package interactionPlugins.programmingExercise.pythonExercise.pyodide

import scala.scalajs.js

object PyodideBackends {

  final case class JsDataVariable(
      varName: String,
      jsTypeOf: String,
      stringRepresentation: String
  )

  trait SyncModuleBackend {
    def moduleName: String

    def handleModuleCall(callbackName: String, args: Seq[JsDataVariable]): js.Any
  }

}
