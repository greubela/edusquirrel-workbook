package it.evadid.facades

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

@js.native trait PyodideRuntime extends js.Object {
  def unregisterJsModule(name: String): Unit = js.native
  def registerJsModule(name: String, module: js.Dictionary[js.Any]): Unit = js.native
  def setStdout(options: js.Object): Unit = js.native
  def setStderr(options: js.Object): Unit = js.native
  def runPython(code: String): js.Any = js.native
  def runPythonAsync(code: String, options: js.Object): js.Promise[js.Any] = js.native
  def toPy(value: js.Any): js.Dynamic = js.native
  def loadPackage(packages: js.Array[String]): js.Promise[js.Any] = js.native
  def loadPackagesFromImports(code: String): js.Promise[js.Any] = js.native
}

/** Worker-local code readiness; interpreter ownership belongs to the execution service. */
object Pyodide extends EvaExternalDependency[PyodideRuntime] {
  private val indexUrl = "https://cdn.jsdelivr.net/pyodide/v0.29.3/full/"
  @js.native @JSGlobalScope private object Scope extends js.Object {
    def importScripts(url: String): Unit = js.native
    def loadPyodide(options: js.Object): js.Promise[PyodideRuntime] = js.native
  }
  override def preload(): Future[Unit] = Future.fromTry(Try {
    if js.typeOf(js.Dynamic.global.globalThis.selectDynamic("loadPyodide")) != "function" then
      Scope.importScripts(indexUrl + "pyodide.js")
  })
  override def createInstance(): Future[PyodideRuntime] =
    preload().flatMap(_ => Scope.loadPyodide(js.Dynamic.literal(indexURL = indexUrl)).toFuture)
}
