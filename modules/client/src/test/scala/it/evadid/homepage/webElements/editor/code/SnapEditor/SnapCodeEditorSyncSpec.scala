package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingStateSnapXmlHelper

import com.raquo.airstream.ownership.{ManualOwner, Owner}
import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.{SnapCodeEditorConfig, SnapCodeEditorImplDelegateToOriginal}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateSnapXml}
import munit.FunSuite
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import scala.scalajs.js

class SnapCodeEditorSyncSpec extends FunSuite {
  private val globals = js.Dynamic.global.globalThis
  private val requireFn = js.Dynamic.global.selectDynamic("require")
  private val root = js.Dynamic.global.process.cwd().asInstanceOf[String]
  private val jsdom = requireFn(root + "/node_modules/jsdom")
  private val window = js.Dynamic.newInstance(jsdom.JSDOM)("<html><body></body></html>").window
  private val names = List("window", "document", "Element", "Node")
  private val previous = names.map(name => name -> globals.selectDynamic(name))

  override def beforeAll(): Unit = {
    names.foreach(name => globals.updateDynamic(name)(window.selectDynamic(name)))
  }

  override def afterAll(): Unit = {
    previous.foreach { (name, value) => globals.updateDynamic(name)(value) }
    window.close()
  }

  private def withKeyboard(test: SnapCodeEditorImplDelegateToOriginal.KeyboardInput => Unit): Unit = {
    val keyboard = dom.document.createElement("textarea").asInstanceOf[SnapCodeEditorImplDelegateToOriginal.KeyboardInput]
    keyboard.id = "morphic_keyboard"
    dom.document.body.appendChild(keyboard)
    try test(keyboard)
    finally keyboard.remove()
  }

  private class RecordingImpl extends SnapCodeEditorImpl {
    var listener: String => Unit = _ => ()
    var current: Option[ProgrammingStateSnapXml] = None
    var pendingXml: Option[String] = None
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
    override def flushPendingProjectChanges(): Unit = {
      val pending = pendingXml
      pendingXml = None
      pending.foreach(listener)
    }
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
    val state = Var[ProgrammingState](ProgrammingStateSnapXmlHelper.mini)
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
    val state = Var[ProgrammingState](ProgrammingStateSnapXmlHelper.mini)
    val impl = new RecordingImpl
    val editor = SnapCodeEditor(state, SnapCodeEditorConfig.Testing, impl)
    val first = new ManualOwner
    editor.mountEditorInto(null, first)
    first.killSubscriptions()
    state.set(ProgrammingStateSnapXmlHelper.empty)
    assertEquals(impl.loadRequests, 0)
    val second = new ManualOwner
    try {
      editor.mountEditorInto(null, second)
      state.set(ProgrammingStateSnapXmlHelper.mini)
      assertEquals(impl.mounts, 2)
      assertEquals(impl.loadRequests, 1)
      assertEquals(impl.reloads, 1)
      assertEquals(impl.librariesCleared, 0)
    } finally second.killSubscriptions()
  }

  test("preview rendering preserves the shared keyboard draft, selection and style") {
    withKeyboard { keyboard =>
      keyboard.value = "12345"
      keyboard.setSelectionRange(1, 4, "backward")
      keyboard.style.cssText = "position: absolute; top: 23px; left: 41px; font-size: 18px; opacity: 0;"
      val style = keyboard.style.cssText
      val world = js.Dynamic.literal()
      keyboard.asInstanceOf[js.Dynamic].updateDynamic("world")(world)
      keyboard.focus()
      val result = SnapCodeEditorImplDelegateToOriginal.preserveKeyboardState {
        keyboard.value = ""
        keyboard.style.top = "0px"
        keyboard.style.left = "0px"
        "rendered"
      }
      assertEquals(result, "rendered")
      assertEquals(keyboard.value, "12345")
      assertEquals(keyboard.selectionStart, 1)
      assertEquals(keyboard.selectionEnd, 4)
      assertEquals(keyboard.selectionDirection, "backward")
      assertEquals(keyboard.style.cssText, style)
      assert(keyboard.asInstanceOf[js.Dynamic].selectDynamic("world") eq world)
      assert(dom.document.activeElement eq keyboard)
    }
  }

  test("preview rendering restores keyboard state when rendering fails") {
    withKeyboard { keyboard =>
      keyboard.value = "9876"
      keyboard.setSelectionRange(2, 3, "forward")
      keyboard.style.cssText = "position: absolute; top: 19px; left: 53px;"
      val style = keyboard.style.cssText
      val error = new IllegalStateException("preview failed")
      val thrown = intercept[IllegalStateException] {
        SnapCodeEditorImplDelegateToOriginal.preserveKeyboardState {
          keyboard.value = ""
          keyboard.style.cssText = ""
          throw error
        }
      }
      assert(thrown eq error)
      assertEquals(keyboard.value, "9876")
      assertEquals(keyboard.selectionStart, 2)
      assertEquals(keyboard.selectionEnd, 3)
      assertEquals(keyboard.selectionDirection, "forward")
      assertEquals(keyboard.style.cssText, style)
    }
  }

  test("preview rendering also works without an existing keyboard") {
    assertEquals(dom.document.getElementById("morphic_keyboard"), null)
    val keyboard = dom.document.createElement("textarea").asInstanceOf[dom.HTMLTextAreaElement]
    keyboard.id = "morphic_keyboard"
    try {
      val result = SnapCodeEditorImplDelegateToOriginal.preserveKeyboardState {
        dom.document.body.appendChild(keyboard)
        keyboard.value = "preview keyboard"
        "rendered"
      }
      assertEquals(result, "rendered")
      assertEquals(dom.document.getElementById("morphic_keyboard"), keyboard)
      assertEquals(keyboard.value, "preview keyboard")
    } finally keyboard.remove()
  }

  test("Snap image regeneration commits only cleaned code and never reloads its own edit") {
    val state = Var[ProgrammingState](ProgrammingStateSnapXmlHelper.mini)
    val impl = new RecordingImpl
    var edits = List.empty[ProgrammingState]
    val editor = SnapCodeEditor(state, SnapCodeEditorConfig.Testing, impl, next => edits = edits :+ next)
    val owner = new ManualOwner
    try {
      editor.mountEditorInto(null, owner)
      def liveXml(code: String, image: Int) = s"<project><thumbnail>image-$image</thumbnail><stage><pentrails>trails-$image</pentrails><scripts>$code</scripts></stage></project>"
      val cleaned = ProgrammingStateSnapXml("<project><stage><scripts>edited</scripts></stage></project>")
      impl.listener(liveXml("edited", 0))
      assertEquals(state.now(), cleaned)
      assertEquals(edits, List(cleaned))
      assertEquals(impl.reloads, 0)
      for (image <- 1 to 20) impl.listener(liveXml("edited", image))
      editor.onFullscreenOpen()
      assertEquals(edits, List(cleaned))
      assertEquals(impl.reloads, 0)
      impl.listener(liveXml("changed again", 21))
      assertEquals(edits.size, 2)
      assertEquals(state.now(), ProgrammingStateSnapXml("<project><stage><scripts>changed again</scripts></stage></project>"))
      assertEquals(impl.reloads, 0)
    } finally owner.killSubscriptions()
  }

  test("cleaning regenerated Snap images retains legacy metadata and authored costumes") {
    val source = ProgrammingStateSnapXml("<project/>", legacyFloatingObjects = List(" watcher ", "comment\nline two"))
    val state = Var[ProgrammingState](source)
    val impl = new RecordingImpl
    var edits = List.empty[ProgrammingState]
    val editor = SnapCodeEditor(state, SnapCodeEditorConfig.Testing, impl, next => edits = edits :+ next)
    val owner = new ManualOwner
    try {
      editor.mountEditorInto(null, owner)
      def liveXml(image: Int) =
        s"<project><thumbnail>image-$image</thumbnail><costumes><costume image=\"authored\"/></costumes><stage><pentrails>trails-$image</pentrails><scripts>edited</scripts></stage></project>"
      val cleaned = source.withProjectXml("<project><costumes><costume image=\"authored\"/></costumes><stage><scripts>edited</scripts></stage></project>")
      impl.listener(liveXml(0))
      impl.listener(liveXml(1))
      editor.onFullscreenOpen()
      assertEquals(state.now(), cleaned)
      assertEquals(edits, List(cleaned))
      assertEquals(impl.current, Some(cleaned.toSnapXml))
      assertEquals(impl.reloads, 0)
      assertEquals(impl.librariesCleared, 0)
      assertEquals(ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(state.now())), cleaned)
    } finally owner.killSubscriptions()
  }

  test("Snap capture and fullscreen close preserve restored legacy metadata") {
    val initial = ProgrammingStateSnapXml("<project/>", legacyFloatingObjects = List(" watcher ", "comment\r\n\t "))
    val state = Var[ProgrammingState](initial)
    val impl = new RecordingImpl
    var edits = List.empty[ProgrammingState]
    val editor = SnapCodeEditor(state, SnapCodeEditorConfig.Testing, impl, next => edits = edits :+ next)
    val owner = new ManualOwner
    try {
      editor.mountEditorInto(null, owner)
      val capturedXml = "<project name=\"captured\"/>"
      impl.current = Some(ProgrammingStateSnapXml(capturedXml))
      val captured = editor.captureCurrentProject()
      assertEquals(captured, initial.withProjectXml(capturedXml))
      assertEquals(state.now(), initial)
      assertEquals(edits, Nil)
      assertEquals(ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(captured)), captured)

      val closed = initial.withProjectXml("<project name=\"closed\"/>")
      impl.pendingXml = Some(closed.snapXml)
      editor.onFullscreenClose()
      assertEquals(state.now(), closed)
      assertEquals(edits, List(closed))
      val restored = ProgrammingStateSnapXml("<project name=\"restored\"/>",
        legacyFloatingObjects = List(" restored watcher\n", " restored comment\r\n"))
      state.set(ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(restored)))
      editor.onFullscreenOpen()
      assertEquals(state.now(), restored)
      assertEquals(impl.current, Some(restored))
      assertEquals(edits, List(closed))

      val reopenedXml = "<project name=\"reopened edit\"/>"
      impl.current = Some(ProgrammingStateSnapXml(reopenedXml))
      assertEquals(editor.captureCurrentProject(), restored.withProjectXml(reopenedXml))
      impl.pendingXml = Some(reopenedXml)
      editor.onFullscreenClose()
      val reopened = restored.withProjectXml(reopenedXml)
      assertEquals(state.now(), reopened)
      assertEquals(edits, List(closed, reopened))
      assertEquals(ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(state.now())), reopened)
    } finally owner.killSubscriptions()
  }
}
