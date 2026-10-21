package it.evadid.homepage.control.startup

import it.evadid.util.JsHelpers.promiseToFuture
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

/** Native runtime creation for legacy main-thread consumers; does not share mutable interpreter instances. */
object PyodideRuntimeLoading {
  @js.native
  trait Pyodide extends js.Object {
    val globals: js.Dynamic = js.native

    def registerJsModule(name: String, module: js.Object): Unit = js.native

    def setStdout(options: js.Object): Unit = js.native

    def setStderr(options: js.Object): Unit = js.native

    def runPythonAsync(code: String): js.Promise[js.Any] = js.native
  }

  @js.native
  @JSGlobal("loadPyodide")
  private def loadPyodide(): js.Promise[Pyodide] = js.native
  def create(): Future[Pyodide] = promiseToFuture(loadPyodide())
}
