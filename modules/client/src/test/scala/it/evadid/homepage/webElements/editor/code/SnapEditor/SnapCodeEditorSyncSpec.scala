package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.{ManualOwner, Owner}
import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.workbook.elements.interactionElements.programming.*
import munit.FunSuite
import org.scalajs.dom.html.Canvas

class SnapCodeEditorSyncSpec extends FunSuite {
  private class RecordingImpl extends SnapCodeEditorImpl {
    var listener: String => Unit = _ => ()
    var current: Option[ProgrammingStateSnapXml] = None
    var mounts = 0
    var renders = 0
    var reloads = 0
    var forcedLoads = 0
    var loadRequests = 0
    var librariesCleared = 0
    override def mount(owner: Owner): Unit = { mounts += 1 }
    override def renderEditorInto(state: ProgrammingStateSnapXml, canvas: Canvas, config: SnapCodeEditorConfig): Unit = {
      renders += 1
      current = Some(state)
    }
    override def loadProgramIfChanged(state: ProgrammingStateSnapXml): Unit = {
      loadRequests += 1
      if !current.contains(state) then {
        reloads += 1
        current = Some(state)
      }
    }
    override def acknowledgeProgramFromEditor(state: ProgrammingStateSnapXml): Unit = { current = Some(state) }
    override def forceLoadProgram(state: ProgrammingStateSnapXml): Unit = { forcedLoads += 1 }
    override def setOnProjectXmlChangedListener(callback: String => Unit): Unit = { listener = callback }
    override def renderPreviewInto(state: ProgrammingStateSnapXml, canvas: Canvas, config: SnapCodeEditorConfig): Unit = ()
    override def flushPendingProjectChanges(): Unit = ()
    override def currentProjectXml(): Option[String] = current.map(_.snapXml)
    override def startWorldCycles(): Unit = ()
    override def pauseWorldCycles(): Unit = ()
    override def runGreenFlagOnStage(canvas: Canvas): Unit = ()
    override def stopGreenFlagOnStage(): Unit = ()
    override def setGreenFlagStepMs(ms: Double): Unit = ()
    override def fitEditorToContainer(): Unit = ()
    override def removeAllLibraries(includeDefaultLibraries: Boolean): Unit = { librariesCleared += 1 }
    override def destroy(): Unit = ()
  }

  test("mount once, publish Snap edits once, and reload only external changes") {
    val state = Var[ProgrammingState](ProgrammingStateSnapXml.mini)
    val impl = new RecordingImpl
    var edits = List.empty[ProgrammingState]
    val editor = SnapCodeEditor(state, SnapCodeEditorConfig.Testing, impl, next => edits = edits :+ next)
    val owner = new ManualOwner
    try {
      editor.mountEditorInto(null, owner)
      assertEquals(impl.renders, 1)
      assertEquals(impl.loadRequests, 0) // no initial signal echo
      val next = ProgrammingStateSnapXml("<project>edited</project>")
      impl.listener(next.snapXml)
      assertEquals(state.now(), next)
      assertEquals(edits, List(next))
      assertEquals(impl.reloads, 0) // acknowledgement must precede the Var update
      assertEquals(impl.renders, 1)
      impl.listener(next.snapXml)
      assertEquals(edits, List(next))
      val restored = ProgrammingStateSnapXml("<project>restored</project>")
      state.set(restored)
      state.set(restored)
      assertEquals(impl.reloads, 1)
      assertEquals(impl.renders, 1)
      editor.onFullscreenOpen()
      assertEquals(impl.forcedLoads, 0)
      assertEquals(impl.reloads, 1)
      assertEquals(impl.librariesCleared, 0)
    } finally owner.killSubscriptions()
  }

  test("a fresh mount owner observes restores after closing and reopening") {
    val state = Var[ProgrammingState](ProgrammingStateSnapXml.mini)
    val impl = new RecordingImpl
    val editor = SnapCodeEditor(state, SnapCodeEditorConfig.Testing, impl)
    val first = new ManualOwner
    editor.mountEditorInto(null, first)
    first.killSubscriptions()
    state.set(ProgrammingStateSnapXml.empty)
    assertEquals(impl.loadRequests, 0)
    val second = new ManualOwner
    try {
      editor.mountEditorInto(null, second)
      state.set(ProgrammingStateSnapXml.mini)
      assertEquals(impl.mounts, 2)
      assertEquals(impl.loadRequests, 1)
      assertEquals(impl.reloads, 1)
      assertEquals(impl.librariesCleared, 0)
    } finally second.killSubscriptions()
  }
}
