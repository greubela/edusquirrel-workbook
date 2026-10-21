package it.evadid.homepage.control.startup

import it.evadid.util.JsHelpers.promiseToFuture
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

/** Pyodide runtime and worker construction; consumers own their interpreter and worker lifetimes. */
object PyodideLoading {
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
  def createRuntime(): Future[Pyodide] = promiseToFuture(loadPyodide())

  def configuredWorkerUrl(globalObject: js.Dynamic = js.Dynamic.global.globalThis): String = {
    val configured = globalObject.selectDynamic("PYODIDE_WORKER_URL")
    if !js.isUndefined(configured) && configured != null then configured.asInstanceOf[String]
    else "../js/pyodide-worker.js"
  }

  def createWorker(url: String): dom.Worker =
    new dom.Worker(url, js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions])
}
