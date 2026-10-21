package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingStateSnapXmlHelper

import com.raquo.airstream.ownership.{ManualOwner, Owner}
import com.raquo.laminar.api.L.*
import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.{CanvasVisibility, SnapCodeEditorConfig, SnapCodeEditorImplDelegateToOriginal}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml, ProgrammingStateSnapScalaModel}
import munit.FunSuite
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import scala.scalajs.js

class SnapCodeEditorSyncSpec extends FunSuite {
  private val logger = it.evadid.util.logging.Logger.withNameAndPrefixes(Some("test"), it.evadid.util.logging.derived.PrintToStdLogger.printNothing)
  private val globals = js.Dynamic.global.globalThis
  private val requireFn = js.Dynamic.global.selectDynamic("require")
  private val root = js.Dynamic.global.process.cwd().asInstanceOf[String]
  private val jsdom = requireFn(root + "/node_modules/jsdom")
  private val window = js.Dynamic.newInstance(jsdom.JSDOM)("<html><body></body></html>").window
  private val names = List("window", "document", "Element", "Node")
  private var previous = List.empty[(String, js.Dynamic)]

  override def beforeAll(): Unit = {
    previous = names.map(name => name -> js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, name))
    names.foreach(name => globals.updateDynamic(name)(window.selectDynamic(name)))
  }

  override def afterAll(): Unit = {
    previous.foreach { (name, descriptor) =>
      if js.isUndefined(descriptor) then js.special.delete(globals, name)
      else js.Dynamic.global.Object.defineProperty(globals, name, descriptor)
    }
    window.close()
  }

  private def withKeyboard(test: it.evadid.facades.Snap.KeyboardInput => Unit): Unit = {
    val keyboard = dom.document.createElement("textarea").asInstanceOf[it.evadid.facades.Snap.KeyboardInput]
    keyboard.id = "morphic_keyboard"
    dom.document.body.appendChild(keyboard)
    try test(keyboard)
    finally keyboard.remove()
  }

  private class RecordingImpl extends SnapCodeEditorImpl {
    override def changeLanguage(language: it.evadid.core.datastructures.language.AppLanguage.HumanLanguage): Unit = ()
    var listener: String => Unit = _ => ()
    var current: Option[ProgrammingStateSnapXml] = None
    var pendingXml: Option[String] = None
    var previewed = List.empty[ProgrammingStateSnapXml]
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
    override def renderPreviewInto(state: ProgrammingStateSnapXml, canvas: Canvas, config: SnapCodeEditorConfig): Unit = { previewed = previewed :+ state }
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

  test("native Snap edits retain typed state, legacy data and authored assets without an echo reload") {
    val source = ProgrammingStateSnapXml("<project/>", List("watcher")).toSnapScalaModel.toOption.get
    val state = Var[ProgrammingState](source)
    val impl = new RecordingImpl
    var edits = List.empty[ProgrammingState]
    val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl)
    val owner = new ManualOwner
    state.signal.changes.distinct.foreach(next => edits = edits :+ next)(using owner)
    try {
      editor.mountEditorInto(null, owner)
      assertEquals(impl.current, Some(source.toSnapXml))
      val live = "<project><thumbnail>generated</thumbnail><costumes><costume name='authored' image='asset'/></costumes></project>"
      impl.listener(live)
      val next = state.now().asInstanceOf[ProgrammingStateSnapScalaModel]
      assertEquals(next.legacyFloatingObjects, source.legacyFloatingObjects)
      assertEquals(next.model.project.get.costumes.head.image, Some("asset"))
      assertEquals(next.model.project.get.thumbnail, None)
      assertEquals(impl.reloads, 0)
      assertEquals(edits, List(next))
      impl.listener(live)
      assertEquals(edits, List(next))
      assertEquals(ProgrammingState.serializer.deserialize(
        ProgrammingState.serializer.serialize(next)), next)
    } finally owner.killSubscriptions()
  }

  test("mount once, publish Snap edits once, and reload only external changes") {
    val state = Var[ProgrammingState](ProgrammingStateSnapXmlHelper.mini)
    val impl = new RecordingImpl
    var edits = List.empty[ProgrammingState]
    val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl)
    val owner = new ManualOwner
    state.signal.changes.distinct.foreach(next => edits = edits :+ next)(using owner)
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
      assertEquals(impl.forcedLoads, 0)
      assertEquals(impl.reloads, 1)
      assertEquals(impl.librariesCleared, 0)
    } finally owner.killSubscriptions()
  }

  test("a fresh mount owner observes restores after closing and reopening") {
    val state = Var[ProgrammingState](ProgrammingStateSnapXmlHelper.mini)
    val impl = new RecordingImpl
    val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl)
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
    val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl)
    val owner = new ManualOwner
    state.signal.changes.distinct.foreach(next => edits = edits :+ next)(using owner)
    try {
      editor.mountEditorInto(null, owner)
      def liveXml(code: String, image: Int) = s"<project><thumbnail>image-$image</thumbnail><stage><pentrails>trails-$image</pentrails><scripts>$code</scripts></stage></project>"
      val cleaned = ProgrammingStateSnapXml("<project><stage><scripts>edited</scripts></stage></project>")
      impl.listener(liveXml("edited", 0))
      assertEquals(state.now(), cleaned)
      assertEquals(edits, List(cleaned))
      assertEquals(impl.reloads, 0)
      for (image <- 1 to 20) impl.listener(liveXml("edited", image))
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
    val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl)
    val owner = new ManualOwner
    state.signal.changes.distinct.foreach(next => edits = edits :+ next)(using owner)
    try {
      editor.mountEditorInto(null, owner)
      def liveXml(image: Int) =
        s"<project><thumbnail>image-$image</thumbnail><costumes><costume image=\"authored\"/></costumes><stage><pentrails>trails-$image</pentrails><scripts>edited</scripts></stage></project>"
      val cleaned = source.withProjectXml("<project><costumes><costume image=\"authored\"/></costumes><stage><scripts>edited</scripts></stage></project>")
      impl.listener(liveXml(0))
      impl.listener(liveXml(1))
      assertEquals(state.now(), cleaned)
      assertEquals(edits, List(cleaned))
      assertEquals(impl.current, Some(cleaned.toSnapXml))
      assertEquals(impl.reloads, 0)
      assertEquals(impl.librariesCleared, 0)
      assertEquals(ProgrammingState.serializer.deserialize(
        ProgrammingState.serializer.serialize(state.now())), cleaned)
    } finally owner.killSubscriptions()
  }

  test("closing an editor before the Snap runtime loads does not touch Process") {
    new SnapCodeEditorImplDelegateToOriginal().pauseWorldCycles()
  }

  test("empty-canvas diagnostics distinguish real blocks from empty project containers") {
    assert(!CanvasVisibility.looksLikeProgramWithBlocks(ProgrammingStateSnapXmlHelper.empty.snapXml))
    assert(CanvasVisibility.looksLikeProgramWithBlocks(ProgrammingStateSnapXmlHelper.mini.snapXml))
    assert(CanvasVisibility.looksLikeProgramWithBlocks("<scripts><custom-block s=\"example\"/></scripts>"))
    assert(!CanvasVisibility.looksLikeProgramWithBlocks("<blocks><block-definition s=\"example\"/></blocks>"))
  }

  private class DeferredImpl extends RecordingImpl {
    var pending = List.empty[(() => Unit, Throwable => Unit)]
    override def whenReady(render: () => Unit, onError: Throwable => Unit): Unit = {
      pending = pending :+ (render, onError)
    }
    def release(): Unit = {
      val callbacks = pending
      pending = Nil
      callbacks.foreach(_._1())
    }
  }

  private def withRoot(test: dom.html.Div => Unit): Unit = {
    val container = dom.document.createElement("div").asInstanceOf[dom.html.Div]
    dom.document.body.appendChild(container)
    try test(container)
    finally container.remove()
  }

  test("text edits retain their exact source without rebuilding an identical block preview") {
    val java = "public class Drawing { static void line(int size) { Turtle.forward(size); } " +
      "public static void main(String[] args) { line(30); } }"
    val python = ProgrammingStateJavaString(java).toPython.code
    for ((source, wrap) <- List[(String, String => ProgrammingState)](
      java -> ProgrammingStateJavaString.apply,
      python -> ProgrammingStatePythonString.apply
    )) withRoot { container =>
      val initial = wrap(source)
      val state = Var[ProgrammingState](initial)
      val impl = new RecordingImpl
      val owner = new ManualOwner
      var received = List.empty[ProgrammingState]
      state.signal.foreach(next => received = received :+ next)(using owner)
      val mounted = render(container, SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl).getDomElement())
      try {
        val canvas = container.querySelector("canvas")
        val drafts = List("\n", "\r\n\t ", "\n\n  ").map(suffix => wrap(source + suffix))
        drafts.foreach { draft =>
          assertEquals(draft.toSnapXml, initial.toSnapXml)
          state.set(draft)
          assert(container.querySelector("canvas") eq canvas)
          assertEquals(state.now(), draft)
          assertEquals(ProgrammingState.serializer.deserialize(
            ProgrammingState.serializer.serialize(state.now())), draft)
        }
        assertEquals(impl.previewed, List(initial.toSnapXml))
        assertEquals(received, initial :: drafts)
      } finally {
        mounted.unmount()
        owner.killSubscriptions()
      }
    }
  }

  test("preview reuse compares complete XML and legacy data across typed Snap representations") {
    withRoot { container =>
      val initial = ProgrammingStateSnapXml(
        "<project><stage><sprites><sprite name=\"Turtle\"><scripts>" +
          "<script x=\"10\" y=\"20\"><block s=\"forward\"><l>30</l></block></script>" +
          "</scripts></sprite></sprites></stage></project>")
      val state = Var[ProgrammingState](initial)
      val impl = new RecordingImpl
      val mounted = render(container, SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl).getDomElement())
      try {
        val canvas = container.querySelector("canvas")
        state.set(initial.toSnapScalaModel.toOption.get)
        state.set(initial.copy())
        assert(container.querySelector("canvas") eq canvas)
        assertEquals(impl.previewed, List(initial))
        val moved = initial.withProjectXml(initial.snapXml.replace("x=\"10\"", "x=\"80\""))
        val legacy = moved.copy(legacyFloatingObjects = List("watcher\r\n\t "))
        val changed = legacy.withProjectXml(legacy.snapXml.replace("<l>30</l>", "<l>40</l>"))
        List(moved, legacy, changed, initial).foreach { next =>
          val previousCanvas = container.querySelector("canvas")
          state.set(next)
          assert(!(container.querySelector("canvas") eq previousCanvas))
          for (_ <- 1 to 5) state.set(next.copy())
          assertEquals(state.now(), next)
        }
        assertEquals(impl.previewed, List(initial, moved, legacy, changed, initial))
      } finally mounted.unmount()
    }
  }

  test("invalid drafts remove the old preview and restoring its source renders it again") {
    withRoot { container =>
      val initial = ProgrammingStateSnapXmlHelper.mini
      val state = Var[ProgrammingState](initial)
      val impl = new RecordingImpl
      val mounted = render(container, SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl).getDomElement())
      try {
        val canvas = container.querySelector("canvas")
        val invalid = ProgrammingStateJavaString("public class Drawing {\r\n\t")
        state.set(invalid)
        assertEquals(container.querySelector("canvas"), null)
        assert(container.textContent.contains("Preview unavailable for this draft."))
        assertEquals(state.now(), invalid)
        state.set(initial)
        assert(!(container.querySelector("canvas") eq canvas))
        assertEquals(impl.previewed, List(initial, initial))
      } finally mounted.unmount()
    }
  }

  test("identical pending previews share readiness and ignore invalidated mounts") {
    withRoot { container =>
      val initial = ProgrammingStateSnapXmlHelper.mini
      val state = Var[ProgrammingState](initial)
      val impl = new DeferredImpl
      val preview = SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl)
      val first = render(container, preview.getDomElement())
      val canvas = container.querySelector("canvas")
      for (_ <- 1 to 5) state.set(initial.copy())
      assert(container.querySelector("canvas") eq canvas)
      assertEquals(impl.pending.size, 1)
      state.set(ProgrammingStateJavaString("public class Drawing {"))
      impl.release()
      assertEquals(impl.previewed, Nil)
      state.set(initial)
      first.unmount()
      impl.release()
      assertEquals(impl.previewed, Nil)
      state.set(ProgrammingStateSnapXmlHelper.empty)
      val second = render(container, preview.getDomElement())
      try {
        impl.release()
        assertEquals(impl.previewed, List(ProgrammingStateSnapXmlHelper.empty))
      } finally second.unmount()
    }
  }

  test("pending previews render only the latest mounted state after runtime readiness") {
    withRoot { container =>
      val initial = ProgrammingStateSnapXmlHelper.mini
      val state = Var[ProgrammingState](initial)
      val impl = new DeferredImpl
      val preview = SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl)
      val mounted = render(container, preview.getDomElement())
      try {
        assertEquals(impl.previewed, Nil)
        state.set(ProgrammingStateSnapXmlHelper.empty)
        impl.release()
        assertEquals(impl.previewed, List(ProgrammingStateSnapXmlHelper.empty))
        state.set(initial)
      } finally mounted.unmount()
      impl.release()
      assertEquals(impl.previewed, List(ProgrammingStateSnapXmlHelper.empty))
    }
  }

  test("destroying an uninitialized native editor does not access the unloaded Snap runtime") {
    assert(js.isUndefined(globals.selectDynamic("SpriteMorph")))
    val impl = new SnapCodeEditorImplDelegateToOriginal()
    impl.destroy()
    impl.destroy()
    assertEquals(impl.currentProjectXml(), None)
  }

  test("closing and reopening while Snap loads mounts only the current canvas") {
    withRoot { container =>
      val state = Var[ProgrammingState](ProgrammingStateSnapXmlHelper.mini)
      val impl = new DeferredImpl
      val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl)
      val first = render(container, editor.getDomElement())
      first.unmount()
      val second = render(container, editor.getDomElement())
      try {
        state.set(ProgrammingStateSnapXmlHelper.empty)
        impl.release()
        assertEquals(impl.renders, 1)
        assertEquals(impl.current, Some(ProgrammingStateSnapXmlHelper.empty))
      } finally second.unmount()
    }
  }

  test("runtime failures produce a visible preview error and allow a later state to recover") {
    withRoot { container =>
      val state = Var[ProgrammingState](ProgrammingStateSnapXmlHelper.mini)
      val impl = new DeferredImpl
      val mounted = render(container, SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl).getDomElement())
      try {
        impl.pending.head._2(new IllegalStateException("runtime failed"))
        assert(container.querySelector("[role=alert]").textContent.contains("runtime failed"))
        state.set(ProgrammingStateSnapXmlHelper.mini.copy())
        impl.release()
        assertEquals(container.querySelector("[role=alert]"), null)
        assertEquals(impl.previewed, List(ProgrammingStateSnapXmlHelper.mini))
        state.set(ProgrammingStateSnapXmlHelper.empty)
        impl.pending.head._2(new IllegalStateException("another failure"))
        state.set(ProgrammingStateJavaString("public class Drawing {"))
        assertEquals(container.querySelector("[role=alert]"), null)
        assert(container.textContent.contains("Preview unavailable for this draft."))
        state.set(ProgrammingStateSnapXmlHelper.mini)
        impl.release()
        assertEquals(impl.previewed, List.fill(2)(ProgrammingStateSnapXmlHelper.mini))
      } finally mounted.unmount()
    }
  }

}
