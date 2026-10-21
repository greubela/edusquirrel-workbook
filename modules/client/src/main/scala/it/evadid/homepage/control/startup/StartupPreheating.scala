package it.evadid.homepage.control.startup

import com.raquo.airstream.state.Var
import org.scalajs.dom
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import it.evadid.homepage.webElements.facades.ExternalDependencies.{CodeMirror, Snap, JSXGraph}

object StartupPreheating {
  private val started = Var(false)
  def start(): Unit = if !started.now() then {
    started.set(true)
    // Start requests immediately; never await them from the render path.
    CodeMirror.preload().failed.foreach(_ => ())
    WorkerPreloading.preheat()
    val turtlePages = List("workbookTest", "workbookEmbroidery", "workbookEmbroideryPreview", "workbookMonks")
    if turtlePages.exists(id => dom.document.getElementById(id) != null) then {
      Snap.preload().failed.foreach(_ => ())
      JSXGraph.preload().failed.foreach(_ => ())
    }
  }
}
