package it.evadid.homepage.control.startup

import it.evadid.homepage.webElements.editor.code.CodeMirrorEditorLoading
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapRuntimeLoading
import it.evadid.util.logging.Logger
import org.scalajs.dom
import todomove.`export`.workers.PyodideWorkerClient
import scala.concurrent.{Future, ExecutionContext}
import scala.scalajs.js

/** Phase two: no editor instances, no readiness dependency for the overview. */
object OptionalRuntimeStartup {
  private given ExecutionContext = ExecutionContext.global
  private var started = false

  private def allowsExternal(origin: String): Boolean = {
    val policy = dom.document.querySelector("meta[http-equiv='Content-Security-Policy']")
    policy == null || Option(policy.getAttribute("content")).exists(_.contains(origin))
  }

  def start(logger: Logger): Unit = if (!started) {
    started = true
    def background(name: String)(work: => Unit): Unit =
      StartupTasks.idle(work).failed.foreach(err => logger.logExceptionWarn(s"$name warmup failed", err))

    if (allowsExternal("https://esm.sh")) background("CodeMirror") { CodeMirrorEditorLoading.preheat() }
    background("Snap") { SnapRuntimeLoading.ready().failed.foreach(err => logger.logExceptionWarn("Snap warmup failed", err)) }
    if (allowsExternal("https://cdn.jsdelivr.net")) background("Pyodide") { PythonCommandWorkerPreheat.preheat().failed.foreach(err => logger.logExceptionWarn("Python warmup failed", err)) }
    background("JSXGraph") {
      BrowserDependencyLoading.script(BrowserDependencyLoading.siteUrl("resources/programs/20261004JSXGraph/jsxgraphcore.js"))
        .failed.foreach(err => logger.logExceptionWarn("JSXGraph warmup failed", err))
    }
    background("Backend worker") {
      ClientStartupConfig.load().foreach { config =>
        val window = dom.window.asInstanceOf[js.Dynamic]
        window.updateDynamic("EDUSQUIRREL_CLIENT_CONFIG")(config)
        if (ClientStartupConfig.workerCount(config) > 0)
          it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo.defaults.workerBackendExecutor

      }
    }
  }
}
