package it.evadid.homepage.webElements.facades

import it.evadid.homepage.control.startup.BrowserLoading
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.Thenable.Implicits.*

@js.native
trait PyodideRuntime extends js.Object {
  val globals: js.Dynamic = js.native

  def registerJsModule(name: String, module: js.Object): Unit = js.native

  def setStdout(options: js.Object): Unit = js.native

  def setStderr(options: js.Object): Unit = js.native

  def runPythonAsync(code: String): js.Promise[js.Any] = js.native
}

object Pyodide extends EvaExternalDependency[PyodideRuntime] {
  @js.native
  @JSGlobal("loadPyodide")
  private def loadPyodide(): js.Promise[PyodideRuntime] = js.native
  private val runtimeUrl = "https://cdn.jsdelivr.net/pyodide/v0.29.3/full/pyodide.js"
  private def available: Boolean = js.typeOf(js.Dynamic.global.globalThis.selectDynamic("loadPyodide")) == "function"
  override def preload(): Future[Unit] = if available then Future.unit else BrowserLoading.load(runtimeUrl)
  override def createInstance(): Future[PyodideRuntime] = preload().flatMap(_ => loadPyodide().toFuture)

  def configuredWorkerUrl(globalObject: js.Dynamic = js.Dynamic.global.globalThis): String = {
    val configured = globalObject.selectDynamic("PYODIDE_WORKER_URL")
    if !js.isUndefined(configured) && configured != null then configured.asInstanceOf[String]
    else "../js/pyodide-worker.js"
  }

  def worker(url: String = configuredWorkerUrl()): ExternalDependency[dom.Worker] =
    Workers.module(url)
}
