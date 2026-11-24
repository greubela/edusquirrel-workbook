package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateSnapScalaModel
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}

import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw, ProgrammingStateSnapScalaModel}

import com.raquo.airstream.ownership.{ManualOwner, Owner}
import com.raquo.laminar.api.L.*
import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.editor.code.SnapEditor.{CanvasVisibility, SnapCodeEditorConfig}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
import it.evadid.core.datastructures.language.AppLanguage.*
import munit.FunSuite
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import scala.scalajs.js
import it.evadid.homepage.webElements.editor.code.EditorDomSuite

class SnapCodeEditorSyncSpec extends FunSuite {
  private val logger = it.evadid.util.logging.Logger.withNameAndPrefixes(Some("test"), it.evadid.util.logging.derived.PrintToStdLogger.printNothing)
  private val globals = js.Dynamic.global.globalThis
  private val window = EditorDomSuite.window
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
  }

  private def withKeyboard(test: it.evadid.facades.Snap.KeyboardInput => Unit): Unit = {
    val keyboard = dom.document.createElement("textarea").asInstanceOf[it.evadid.facades.Snap.KeyboardInput]
    keyboard.id = "morphic_keyboard"
    dom.document.body.appendChild(keyboard)
    try test(keyboard)
    finally keyboard.remove()
  }

  private class RecordingImpl extends SnapCodeEditorImpl {
    val languages = Var(List.empty[HumanLanguage])
    override def changeLanguage(language: HumanLanguage): Unit = languages.update(_ :+ language)
    var listener: String => Unit = _ => ()
    var current: Option[ProgrammingStateRaw] = None
    var pendingXml: Option[String] = None
    var previewed = List.empty[ProgrammingStateRaw]
    var mounts = 0
    var renders = 0
    var reloads = 0
    var forcedLoads = 0
    var loadRequests = 0
    var librariesCleared = 0
    override def mount(owner: Owner): Unit = { mounts += 1 }
    override def renderEditorInto(state: ProgrammingStateRaw, canvas: Canvas, config: SnapCodeEditorConfig): Unit = {
      renders += 1
      current = Some(state)
    }
    override def loadProgramIfChanged(state: ProgrammingStateRaw): Unit = {
      loadRequests += 1
      if !current.contains(state) then {
        reloads += 1
        current = Some(state)
      }
    }
    override def acknowledgeProgramFromEditor(state: ProgrammingStateRaw): Unit = { current = Some(state) }
    override def forceLoadProgram(state: ProgrammingStateRaw): Unit = { forcedLoads += 1 }
    override def setOnProjectXmlChangedListener(callback: String => Unit): Unit = { listener = callback }
    override def renderPreviewInto(state: ProgrammingStateRaw, canvas: Canvas, config: SnapCodeEditorConfig): Unit = { previewed = previewed :+ state }
    override def flushPendingProjectChanges(): Unit = {
      val pending = pendingXml
      pendingXml = None
      pending.foreach(listener)
    }
    override def currentProjectXml(): Option[String] = current.map(_.code)
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
    val state = Var[ProgrammingState](ProgrammingStateSnapScalaModel.mini.toSnapXml)
    val impl = new RecordingImpl
    var edits = List.empty[ProgrammingState]
    val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl, Val("loading"))
    val owner = new ManualOwner
    state.signal.changes.distinct.foreach(next => edits = edits :+ next)(using owner)
    try {
      editor.mountEditorInto(null, owner)
      assertEquals(impl.renders, 1)
      assertEquals(impl.loadRequests, 0) // no initial signal echo
      val next = ProgrammingStateRaw(SnapLanguage, "<project>edited</project>").toSnapScalaModel.toOption.get
      impl.listener(next.toSnapXml.code)
      assertEquals(state.now(), next)
      assertEquals(edits, List(next))
      assertEquals(impl.reloads, 0) // acknowledgement must precede the Var update
      assertEquals(impl.renders, 1)
      impl.listener(next.toSnapXml.code)
      assertEquals(edits, List(next))
      val restored = ProgrammingStateRaw(SnapLanguage, "<project>restored</project>")
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
    val state = Var[ProgrammingState](ProgrammingStateSnapScalaModel.mini.toSnapXml)
    val impl = new RecordingImpl
    val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl, Val("loading"))
    val first = new ManualOwner
    editor.mountEditorInto(null, first)
    first.killSubscriptions()
    state.set(ProgrammingStateSnapScalaModel.empty.toSnapXml)
    assertEquals(impl.loadRequests, 0)
    val second = new ManualOwner
    try {
      editor.mountEditorInto(null, second)
      state.set(ProgrammingStateSnapScalaModel.mini.toSnapXml)
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
      val result = SnapPreviewRenderer.preserveKeyboardState {
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
        SnapPreviewRenderer.preserveKeyboardState {
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
      val result = SnapPreviewRenderer.preserveKeyboardState {
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
    val state = Var[ProgrammingState](ProgrammingStateSnapScalaModel.mini.toSnapXml)
    val impl = new RecordingImpl
    var edits = List.empty[ProgrammingState]
    val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl, Val("loading"))
    val owner = new ManualOwner
    state.signal.changes.distinct.foreach(next => edits = edits :+ next)(using owner)
    try {
      editor.mountEditorInto(null, owner)
      def liveXml(code: String, image: Int) = s"<project><thumbnail>image-$image</thumbnail><stage><pentrails>trails-$image</pentrails><scripts>$code</scripts></stage></project>"
      val cleaned = ProgrammingStateRaw(SnapLanguage, "<project><stage><scripts>edited</scripts></stage></project>").toSnapScalaModel.toOption.get
      impl.listener(liveXml("edited", 0))
      assertEquals(state.now(), cleaned)
      assertEquals(edits, List(cleaned))
      assertEquals(impl.reloads, 0)
      for (image <- 1 to 20) impl.listener(liveXml("edited", image))
      assertEquals(edits, List(cleaned))
      assertEquals(impl.reloads, 0)
      impl.listener(liveXml("changed again", 21))
      assertEquals(edits.size, 2)
      assertEquals(state.now(), ProgrammingStateRaw(SnapLanguage, "<project><stage><scripts>changed again</scripts></stage></project>").toSnapScalaModel.toOption.get)
      assertEquals(impl.reloads, 0)
    } finally owner.killSubscriptions()
  }

  test("closing an editor before the Snap runtime loads does not touch Process") {
    new SnapMorphicSession().pauseWorldCycles()
  }

  test("empty-canvas diagnostics distinguish real blocks from empty project containers") {
    assert(!CanvasVisibility.looksLikeProgramWithBlocks("<project><scenes><scene><stage><sprites><sprite><scripts></scripts></sprite></sprites></stage></scene></scenes></project>"))
    // An empty program still shows its green-flag hat.
    assert(CanvasVisibility.looksLikeProgramWithBlocks(ProgrammingStateSnapScalaModel.empty.toSnapXml.code))
    assert(CanvasVisibility.looksLikeProgramWithBlocks(ProgrammingStateSnapScalaModel.mini.toSnapXml.code))
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
    val python = ProgrammingStateRaw(Java, java).toPython.code
    for ((source, wrap) <- List[(String, String => ProgrammingState)](
      java -> ((code: String) => ProgrammingStateRaw(Java, code)),
      python -> ((code: String) => ProgrammingStateRaw(Python, code))
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

  test("invalid drafts remove the old preview and restoring its source renders it again") {
    withRoot { container =>
      val initial = ProgrammingStateSnapScalaModel.mini.toSnapXml
      val state = Var[ProgrammingState](initial)
      val impl = new RecordingImpl
      val mounted = render(container, SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl).getDomElement())
      try {
        val canvas = container.querySelector("canvas")
        val invalid = ProgrammingStateRaw(Java, "public class Drawing {\r\n\t")
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
      val initial = ProgrammingStateSnapScalaModel.mini.toSnapXml
      val state = Var[ProgrammingState](initial)
      val impl = new DeferredImpl
      val preview = SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl)
      val first = render(container, preview.getDomElement())
      val canvas = container.querySelector("canvas")
      for (_ <- 1 to 5) state.set(initial.copy())
      assert(container.querySelector("canvas") eq canvas)
      assertEquals(impl.pending.size, 1)
      state.set(ProgrammingStateRaw(Java, "public class Drawing {"))
      impl.release()
      assertEquals(impl.previewed, Nil)
      state.set(initial)
      first.unmount()
      impl.release()
      assertEquals(impl.previewed, Nil)
      state.set(ProgrammingStateSnapScalaModel.empty.toSnapXml)
      val second = render(container, preview.getDomElement())
      try {
        impl.release()
        assertEquals(impl.previewed, List(ProgrammingStateSnapScalaModel.empty.toSnapXml))
      } finally second.unmount()
    }
  }

  test("pending previews render only the latest mounted state after runtime readiness") {
    withRoot { container =>
      val initial = ProgrammingStateSnapScalaModel.mini.toSnapXml
      val state = Var[ProgrammingState](initial)
      val impl = new DeferredImpl
      val preview = SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl)
      val mounted = render(container, preview.getDomElement())
      try {
        assertEquals(impl.previewed, Nil)
        state.set(ProgrammingStateSnapScalaModel.empty.toSnapXml)
        impl.release()
        assertEquals(impl.previewed, List(ProgrammingStateSnapScalaModel.empty.toSnapXml))
        state.set(initial)
      } finally mounted.unmount()
      impl.release()
      assertEquals(impl.previewed, List(ProgrammingStateSnapScalaModel.empty.toSnapXml))
    }
  }

  test("destroying an uninitialized native editor does not access the unloaded Snap runtime") {
    assert(js.isUndefined(globals.selectDynamic("SpriteMorph")))
    val impl = new SnapMorphicSession()
    impl.destroy()
    impl.destroy()
    assertEquals(impl.currentProjectXml(), None)
  }

  test("closing and reopening while Snap loads mounts only the current canvas") {
    withRoot { container =>
      val state = Var[ProgrammingState](ProgrammingStateSnapScalaModel.mini.toSnapXml)
      val impl = new DeferredImpl
      val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl, Val("loading"))
      val first = render(container, editor.getDomElement())
      first.unmount()
      val second = render(container, editor.getDomElement())
      try {
        state.set(ProgrammingStateSnapScalaModel.empty.toSnapXml)
        impl.release()
        assertEquals(impl.renders, 1)
        assertEquals(impl.current, Some(ProgrammingStateSnapScalaModel.empty.toSnapXml))
      } finally second.unmount()
    }
  }

  test("runtime failures produce a visible preview error and allow a later state to recover") {
    withRoot { container =>
      val state = Var[ProgrammingState](ProgrammingStateSnapScalaModel.mini.toSnapXml)
      val impl = new DeferredImpl
      val mounted = render(container, SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl).getDomElement())
      try {
        impl.pending.head._2(new IllegalStateException("runtime failed"))
        assert(container.querySelector("[role=alert]").textContent.contains("runtime failed"))
        state.set(ProgrammingStateSnapScalaModel.mini.toSnapXml.copy())
        impl.release()
        assertEquals(container.querySelector("[role=alert]"), null)
        assertEquals(impl.previewed, List(ProgrammingStateSnapScalaModel.mini.toSnapXml))
        state.set(ProgrammingStateSnapScalaModel.empty.toSnapXml)
        impl.pending.head._2(new IllegalStateException("another failure"))
        state.set(ProgrammingStateRaw(Java, "public class Drawing {"))
        assertEquals(container.querySelector("[role=alert]"), null)
        assert(container.textContent.contains("Preview unavailable for this draft."))
        state.set(ProgrammingStateSnapScalaModel.mini.toSnapXml)
        impl.release()
        assertEquals(impl.previewed, List.fill(2)(ProgrammingStateSnapScalaModel.mini.toSnapXml))
      } finally mounted.unmount()
    }
  }

  test("leaving the native keyboard publishes pending edits before another control reads the answer") {
    withRoot { container =>
      val state = Var[ProgrammingState](ProgrammingStateSnapScalaModel.mini.toSnapXml)
      val impl = new RecordingImpl
      val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl, Val("loading"))
      val mounted = render(container, editor.getDomElement())
      try withKeyboard { keyboard =>
        container.querySelector(".be-program-snap-renderer--editor").appendChild(keyboard)
        val pending = ProgrammingStateRaw(SnapLanguage, "<project><notes>pending edit</notes></project>")
        impl.pendingXml = Some(pending.code)
        keyboard.dispatchEvent(js.Dynamic.newInstance(window.Event)("blur").asInstanceOf[dom.Event])
        assertEquals(state.now(), pending.toSnapScalaModel.toOption.get)
        assertEquals(impl.pendingXml, None)
        assertEquals(impl.reloads, 0)
      } finally mounted.unmount()
    }
  }

  test("the editor observes the language signal without remounting or changing the answer") {
    withRoot { container =>
      val initial = ProgrammingStateSnapScalaModel.mini.toSnapXml
      val state = Var[ProgrammingState](initial)
      val language = Var[HumanLanguage](German)
      val impl = new RecordingImpl
      val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl, Val("loading"), language.signal)
      val mounted = render(container, editor.getDomElement())
      try {
        assertEquals(impl.languages.now(), List(German))
        language.set(French)
        language.set(French)
        language.set(English)
        assertEquals(impl.languages.now(), List(German, French, English))
        assertEquals(impl.renders, 1)
        assertEquals(impl.mounts, 1)
        assertEquals(state.now(), initial)
      } finally mounted.unmount()
      language.set(Spanish)
      assertEquals(impl.languages.now(), List(German, French, English))
    }
  }

  test("an unchanged project gets a fresh preview when the language changes") {
    withRoot { container =>
      val initial = ProgrammingStateSnapScalaModel.mini.toSnapXml
      val state = Var[ProgrammingState](initial)
      val language = Var[HumanLanguage](German)
      val impl = new RecordingImpl
      val mounted = render(container, SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl, language.signal).getDomElement())
      try {
        val original = container.querySelector("canvas")
        language.set(Russian)
        assert(!(container.querySelector("canvas") eq original))
        assertEquals(impl.languages.now(), List(German, Russian))
        assertEquals(impl.previewed, List(initial, initial))
        assertEquals(state.now(), initial)
        val russian = container.querySelector("canvas")
        language.set(Russian)
        assert(container.querySelector("canvas") eq russian)
      } finally mounted.unmount()
      language.set(English)
      assertEquals(impl.languages.now(), List(German, Russian))
    }
  }

  test("late dictionaries cannot render an outdated editor language or update a closed mount") {
    withRoot { container =>
      val state = Var[ProgrammingState](ProgrammingStateSnapScalaModel.mini.toSnapXml)
      val language = Var[HumanLanguage](German)
      val impl = new DeferredImpl
      val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl, Val("loading"), language.signal)
      val mounted = render(container, editor.getDomElement())
      language.set(Spanish)
      impl.release()
      assertEquals(impl.languages.now(), List(Spanish))
      assertEquals(impl.renders, 1)
      language.set(French)
      mounted.unmount()
      impl.release()
      assertEquals(impl.languages.now(), List(Spanish))
    }
  }

  test("late dictionaries cannot repaint an outdated preview language") {
    withRoot { container =>
      val initial = ProgrammingStateSnapScalaModel.mini.toSnapXml
      val state = Var[ProgrammingState](initial)
      val language = Var[HumanLanguage](German)
      val impl = new DeferredImpl
      val mounted = render(container, SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, impl, language.signal).getDomElement())
      try {
        language.set(Ukrainian)
        impl.release()
        assertEquals(impl.languages.now(), List(Ukrainian))
        assertEquals(impl.previewed, List(initial))
      } finally mounted.unmount()
    }
  }

  test("the typed interaction view publishes only meaningful changes and excludes generated media") {
    val xml = "<project><stage name='Turtle' tempo='60'><scripts><script><block s='forward'><l>10</l></block></script></scripts></stage></project>"
    val source = ProgrammingStateRaw(SnapLanguage, xml)
    val state = Var[ProgrammingState](source)
    val impl = new RecordingImpl
    val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl, Val("loading"))
    val changes = Var(List.empty[ProgrammingState])
    val owner = new ManualOwner
    state.signal.changes.foreach(value => changes.update(_ :+ value))(using owner)
    try {
      editor.mountEditorInto(null, owner)
      val cosmetic = xml.replace("name='Turtle' tempo='60'", "tempo=\"60\" name=\"Turtle\"")
        .replace("</project>", "<thumbnail>huge</thumbnail></project>")
      impl.listener(cosmetic)
      assertEquals(changes.now(), Nil)
      assertEquals(state.now(), source)
      val next = cosmetic.replace("<l>10</l>", "<l>20</l>")
      impl.listener(next)
      val typed = state.now().asInstanceOf[ProgrammingStateSnapScalaModel]
      assertEquals(typed, editor.snapState.now())
      assert(!typed.toSnapXml.code.contains("huge"))
      assertEquals(changes.now(), List(typed))
      impl.listener(next.replace("huge", "another-preview"))
      assertEquals(changes.now(), List(typed))
      assertEquals(impl.reloads, 0)
      state.set(ProgrammingStateRaw(Python, "unfinished python"))
      impl.listener(next.replace("20", "30"))
      assertEquals(state.now(), ProgrammingStateRaw(Python, "unfinished python"))
    } finally owner.killSubscriptions()
  }

  test("legacy expression answers render through the typed Snap view without replacing their program") {
    val original = ProgrammingStateSnapScalaModel.mini.toSnapXml.toBeExpression
    val state = Var[ProgrammingState](original)
    val impl = new RecordingImpl
    val editor = SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, impl, Val("loading"))
    val owner = new ManualOwner
    try {
      editor.mountEditorInto(null, owner)
      assertEquals(impl.current, Some(original.toSnapXml.removeBloatFromXml))
      assertEquals(state.now(), original)
    } finally owner.killSubscriptions()
  }

}
