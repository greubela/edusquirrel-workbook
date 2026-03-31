package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L.Var
import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.*

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

object PyodideEnvironment {

  case class JsDataVariable(varName: String, jsTypeOf: String, stringRepresentation: String)

  trait AsyncModuleBackend() {
    def moduleName: String

    def handleModuleCall(callbackName: String, args: Seq[JsDataVariable]): Unit
  }

  trait SyncModuleBackend {
    def moduleName: String

    def handleModuleCall(callbackName: String, args: Seq[JsDataVariable]): js.Any
  }

  @js.native
  trait Pyodide extends js.Object {
    def registerJsModule(name: String, module: js.Object): Unit = js.native

    def setStdout(options: js.Object): Unit = js.native

    def setStderr(options: js.Object): Unit = js.native

    def runPython(code: String): js.Any = js.native

    def runPythonAsync(code: String, options: js.Object): js.Promise[js.Any] = js.native
  }

  @js.native
  @JSGlobal("loadPyodide")
  def loadPyodide(): js.Promise[Pyodide] = js.native
}

trait PyodideEnvironment {

  def register(asyncBackend: AsyncModuleBackend): Unit

  def register(syncBackend: SyncModuleBackend): Unit

  def executeCodeFull(request: PythonExecutionRequest): Future[PythonExecutionResult]

  def executeCodeLinewise(request: PythonExecutionRequest, updateAtLeastEveryNLines: Int = 1): Var[PythonExecutionResult]

  def executeUnitTestsFull(pythonCode: PythonExecutionRequest, unitTests: List[PythonUnitTest]): Future[PythonUnitTestResult]

  def executeUnitTestLinewise(pythonCode: PythonExecutionRequest, unitTests: List[PythonUnitTest], updateAtLeastEveryNLines: Int = 100): Var[PythonUnitTestResult]

  def resetImportsAndState(): Unit

  def destroy(): Unit

}
