package it.evadid.homepage.control.startup

import org.scalajs.dom
import scala.scalajs.js

/** Worker construction and asset configuration; request/lifecycle ownership stays in the client. */
object PyodideWorkerLoading {
  def configuredWorkerUrl(globalObject: js.Dynamic = js.Dynamic.global.globalThis): String = {
    val configured = globalObject.selectDynamic("PYODIDE_WORKER_URL")
    if !js.isUndefined(configured) && configured != null then configured.asInstanceOf[String]
    else "../js/pyodide-worker.js"
  }

  def create(url: String): dom.Worker =
    new dom.Worker(url, js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions])
}
