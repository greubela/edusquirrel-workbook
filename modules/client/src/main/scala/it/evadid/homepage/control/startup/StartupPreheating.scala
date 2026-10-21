package it.evadid.homepage.control.startup

import com.raquo.airstream.state.Var
import org.scalajs.dom

object StartupPreheating {
  private val started = Var(false)
  def start(): Unit = if !started.now() then {
    started.set(true)
    // Start requests immediately; never await them from the render path.
    CodeMirrorEditorLoading.preheat()
    WorkerPreloading.preheat()
    val turtlePages = List("workbookTest", "workbookEmbroidery", "workbookEmbroideryPreview", "workbookMonks")
    if turtlePages.exists(id => dom.document.getElementById(id) != null) then {
      SnapRuntimeLoading.preheat()
      GraphRuntimeLoading.preheat()
    }
  }
}
