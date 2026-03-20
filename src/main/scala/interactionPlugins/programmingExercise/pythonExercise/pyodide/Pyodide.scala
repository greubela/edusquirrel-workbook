package interactionPlugins.programmingExercise.pythonExercise.pyodide

import scala.scalajs.js

@js.native
trait Pyodide extends js.Object {
  def registerJsModule(name: String, module: js.Object): Unit = js.native
  def setStdout(options: js.Object): Unit = js.native
  def setStderr(options: js.Object): Unit = js.native
  def runPython(code: String): js.Any = js.native
  def runPythonAsync(code: String, options: js.Object): js.Promise[js.Any] = js.native
}
