package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.homepage.webElements.editor.code.EvaEditor.*
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExerciseFullJava
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.interaction.sync.{SyncControl, UpdateImportance}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory, InteractionVariableState, InteractionVariableUntyped}
import munit.FunSuite
import org.scalajs.dom
import java.time.LocalDateTime
import scala.concurrent.Future
import scala.scalajs.js

class EvaEditorSpec extends FunSuite {
  private val logger = Logger.withNameAndPrefixes(Some("EvaEditorSpec"), PrintToStdLogger.printNothing)
  private val globals = js.Dynamic.global.globalThis
  private val names = List("window", "document", "Element", "Node")
  private val previous = Var(List.empty[(String, js.Dynamic)])
  private lazy val window = {
    val req = js.Dynamic.global.selectDynamic("require")
    val root = js.Dynamic.global.process.cwd().asInstanceOf[String]
    js.Dynamic.newInstance(req(root + "/node_modules/jsdom").JSDOM)("<html><body></body></html>").window
  }
  override def beforeAll(): Unit = {
    previous.set(names.map(name => name -> js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, name)))
    names.foreach(name => globals.updateDynamic(name)(if name == "window" then window else window.selectDynamic(name)))
  }
  override def afterAll(): Unit = {
    previous.now().foreach { (name, descriptor) =>
      if js.isUndefined(descriptor) then js.special.delete(globals, name)
      else js.Dynamic.global.Object.defineProperty(globals, name, descriptor)
    }
    window.close()
  }

  private class TestTab(language: ProgrammingLanguage) extends EvaEditorTab[ProgrammingState] {
    val value = Var[ProgrammingState](ProgrammingStatePythonString(""))
    override protected val underlyingVar = value
    override val logger: Logger = EvaEditorSpec.this.logger
    override val label = language.toString
    val renders = Var(0)
    val opens = Var(0)
    val closes = Var(0)
    val onCapture = Var[() => Unit](() => ())
    val onClose = Var[() => Unit](() => ())
    override def toProgram(value: ProgrammingState): ProgrammingState = value
    override def fromProgram(value: ProgrammingState): ProgrammingState = value.toLanguage(language)
    override def captureSource(): ProgrammingState = { onCapture.now()(); value.now() }
    override def getDomElement(): Element = { renders.update(_ + 1); div(cls := "test-tab", language.toString) }
    override def onFullscreenOpen(): Unit = opens.update(_ + 1)
    override def onFullscreenClose(): Unit = { closes.update(_ + 1); onClose.now()() }
  }
  private class Fixture(source: ProgrammingState, languages: List[ProgrammingLanguage] = List(SnapLanguage, Python, Java),
      modules: Seq[EvaEditorModule] = Nil) {
    val state = Var(source)
    val tabs = languages.distinct.map(l => l -> new TestTab(l)).toMap
    val editor = EvaEditor(logger, state, modules, EvaEditorConfig(enabledLanguages = languages), Some(tabs.apply))
    val host = dom.document.createElement("div")
    dom.document.body.appendChild(host)
    private val root = render(host, editor.getDomElement())
    def close(): Unit = { root.unmount(); host.remove() }
  }

  private class RecordingSyncControl extends SyncControl {
    val stores = Var(Vector.empty[List[InteractionVariableUntyped]])
    override val syncLogger: SyncLogger = SyncLogger(logger)
    override def controlLogger: Logger = logger
    override def requestStore(from: List[InteractionVariableUntyped]): Future[?] = {
      stores.update(_ :+ from)
      Future.unit
    }
    override def ensureCachesAreAtLeastThisRecent(maxAge: LocalDateTime): Future[?] =
      throw IllegalStateException("Unexpected cache refresh.")
    override def ensureCachesContainLastElementsToWrite(variables: List[InteractionVariableUntyped]): Future[?] =
      throw IllegalStateException("Unexpected cache store check.")
    override def createObservableReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): ObservableValue[SyncControl.InteractionVariableSyncReport[T, GA, SA]] =
      throw IllegalStateException("Unexpected observable sync report.")
    override def createCurrentReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): SyncControl.InteractionVariableSyncReport[T, GA, SA] =
      throw IllegalStateException("Unexpected current sync report.")
  }

  test("bound raw drafts publish one signal, history event and store request per edit") {
    val drafts: List[(ProgrammingState, ProgrammingState)] = List(
      ProgrammingStateJavaString("\r\n\tclass Drawing {\r\n ") ->
        ProgrammingStateJavaString("\r\n\tclass Drawing {\r\n  static void draw(\r\n\t "),
      ProgrammingStatePythonString("\r\n# draft\r\n\tdef draw(\r\n ") ->
        ProgrammingStatePythonString("\r\n# draft\r\n\tdef draw(side\r\n\t "),
      ProgrammingStateSnapXml("\r\n<!-- draft -->\r\n<project><stage", List(" watcher A ")) ->
        ProgrammingStateSnapXml("\r\n<!-- draft -->\r\n<project><stage ", List(" watcher B ")))
    drafts.zipWithIndex.foreach { case ((initial, edited), index) =>
      val variable = ProgrammingExerciseFullJava(s"raw-binding-$index").interactionVariable
      val timestamp = LocalDateTime.now().minusSeconds(10)
      val initialEvent = InteractionVariableState[ProgrammingState](initial, UpdateImportance.MAJOR, timestamp)
      variable.updateHistory(_ => InteractionVariableHistory(Set(initialEvent), Map.empty, Map.empty))
      val sync = new RecordingSyncControl
      val bound = variable.createBoundStateWithUpdateImportance(sync, UpdateImportance.MAJOR).toAirstreamVar
      val owner = new ManualOwner
      val observed = Var(Vector.empty[ProgrammingState])
      try {
        bound.signal.foreach(next => observed.update(_ :+ next))(using owner)
        assertEquals(observed.now(), Vector(initial))
        assertEquals(variable.history.events, Set(initialEvent))
        assertEquals(sync.stores.now().size, 0)
        bound.set(edited)
        assertEquals(observed.now(), Vector(initial, edited))
        assertEquals(variable.currentValue, edited)
        assertEquals(variable.history.events.size, 2)
        assertEquals(variable.history.lastStateOption.map(_.updateImportance), Some(UpdateImportance.MAJOR))
        assertEquals(sync.stores.now().size, 1)
        assertEquals(sync.stores.now().head.size, 1)
        assert(sync.stores.now().head.head eq variable)
        assertEquals(ProgrammingState.serializer.serialize(bound.now()), ProgrammingState.serializer.serialize(edited))
        assertEquals(bound.now().fingerprint(), edited.fingerprint())
        val restoreA = InteractionVariableState[ProgrammingState](initial, UpdateImportance.MAJOR, timestamp.plusSeconds(20))
        val restoreB = InteractionVariableState[ProgrammingState](edited, UpdateImportance.MAJOR, timestamp.plusSeconds(21))
        variable.updateHistory(_.withAddedEvent(restoreA))
        variable.updateHistory(_.withAddedEvent(restoreB))
        variable.updateHistory(_.withAddedEvent(restoreB))
        assertEquals(observed.now(), Vector(initial, edited, initial, edited))
        assertEquals(bound.now(), edited)
        assertEquals(variable.history.events.size, 4)
        assertEquals(variable.history.lastStateOption, Some(restoreB))
        assertEquals(sync.stores.now().size, 1)
        assertEquals(variable.deserializeHistory(variable.serializeHistory(variable.history)).lastStateOption.map(_.value), Some(edited))
      } finally owner.killSubscriptions()
    }
  }

  test("State and Var bindings preserve both directions and coupled drafts without echoes") {
    val initial = "\r\n\t# invalid draft\r\nclass Drawing {\r\n "
    val edited = "\r\n\t# invalid draft\r\nclass Drawing {\r\n\t "
    val restored = "\n<!-- restore -->\r\n<project><stage\t "
    List(false, true).foreach { fromVar =>
      val original = Var(initial)
      val state = if fromVar then original.toState else State(initial)
      val first = if fromVar then original else state.toAirstreamVar
      val second = state.toAirstreamVar
      val owner = new ManualOwner
      val firstValues = Var(Vector.empty[String])
      val secondValues = Var(Vector.empty[String])
      val stateValues = Var(Vector.empty[String])
      val subscription = state.observable.addObserver(next => stateValues.update(_ :+ next))
      try {
        first.signal.foreach(next => firstValues.update(_ :+ next))(using owner)
        second.signal.foreach(next => secondValues.update(_ :+ next))(using owner)
        first.set(edited)
        state.set(initial)
        second.set(restored)
        state.set(initial)
        state.set(initial)
        val expected = Vector(initial, edited, initial, restored, initial)
        assertEquals(firstValues.now(), expected)
        assertEquals(secondValues.now(), expected)
        assertEquals(stateValues.now(), expected)
        assertEquals(state.now(), initial)
        assertEquals(first.now(), initial)
        assertEquals(second.now(), initial)
      } finally { subscription.cancel(); owner.killSubscriptions() }
    }
  }

  test("nested Var observers retain the final State value after an A-to-B-to-A update") {
    val initial = ProgrammingStateJavaString("\r\n\tclass Drawing {\r\n ")
    val trigger = ProgrammingStateJavaString("\r\n\tclass Trigger {\r\n ")
    val intermediate = ProgrammingStateJavaString("\r\n\tclass Intermediate {\r\n ")
    val state = State[ProgrammingState](initial)
    val bound = state.toAirstreamVar
    val mirror = state.toAirstreamVar
    val owner = new ManualOwner
    val entered = Var(false)
    val observed = Var(Vector.empty[ProgrammingState])
    try {
      bound.signal.foreach { next =>
        observed.update(_ :+ next)
        if next == trigger && !entered.now() then {
          entered.set(true)
          state.set(intermediate)
          state.set(initial)
        }
      }(using owner)
      bound.set(trigger)
      assert(entered.now())
      assertEquals(state.now(), initial)
      assertEquals(bound.now(), initial)
      assertEquals(mirror.now(), initial)
      assertEquals(observed.now().last, initial)
      assertEquals(ProgrammingState.serializer.serialize(bound.now()), ProgrammingState.serializer.serialize(initial))
      assertEquals(bound.now().fingerprint(), initial.fingerprint())
    } finally owner.killSubscriptions()
  }

  test("derived bindings retain timestamp and metadata edits with unchanged draft text") {
    case class Draft(text: String, timestamp: LocalDateTime, metadata: String)
    val initial = Draft("\r\n\tdef draw(\r\n ", LocalDateTime.of(2026, 10, 13, 10, 0), "remote")
    val updated = initial.copy(timestamp = initial.timestamp.plusSeconds(1), metadata = "local")
    val state = State(initial)
    val derived = state.biMap[Draft](draft => draft.copy(text = "view:" + draft.text),
      draft => draft.copy(text = draft.text.stripPrefix("view:")))
    val bound = derived.toAirstreamVar
    val owner = new ManualOwner
    val observed = Var(Vector.empty[Draft])
    try {
      bound.signal.foreach(next => observed.update(_ :+ next))(using owner)
      bound.set(updated.copy(text = "view:" + updated.text))
      assertEquals(state.now(), updated)
      state.set(initial)
      assertEquals(observed.now(), Vector(initial, updated, initial).map(draft => draft.copy(text = "view:" + draft.text)))
      assertEquals(state.now(), initial)
      assertEquals(bound.now(), derived.now())
    } finally owner.killSubscriptions()
  }

  test("a bound draft repairs a failed derived getter even when its displayed value is unchanged") {
    List(0, 1).foreach { repaired =>
      val source = State("0")
      val numeric = source.biMap[Int](_.toInt, _.toString)
      val bound = numeric.toAirstreamVar
      val owner = new ManualOwner
      val observed = Var(Vector.empty[Int])
      try {
        bound.signal.foreach(next => observed.update(_ :+ next))(using owner)
        source.set("unfinished")
        assertEquals(numeric.observable.now(), None)
        intercept[NumberFormatException](numeric.now())
        assertEquals(bound.now(), 0)
        bound.set(repaired)
        assertEquals(source.now(), repaired.toString)
        assertEquals(numeric.now(), repaired)
        assertEquals(bound.now(), repaired)
        assertEquals(observed.now(), Vector(0, repaired))
      } finally owner.killSubscriptions()
    }
  }

  test("the matching tab opens raw unfinished source and controls stay in configured order") {
    val source = ProgrammingStateJavaString("\r\n class Drawing {\t")
    val f = new Fixture(source, List(Java, Python, SnapLanguage, Java))
    try {
      assertEquals(f.editor.selectedLanguage, Some(Java))
      assertEquals(f.tabs(Java).value.now(), source)
      assertEquals(f.state.now(), source)
      assertEquals(f.host.querySelectorAll(".eva-editor__tabs button").length, 3)
      assertEquals(f.host.querySelector(".eva-editor__tabs button").textContent, Java.toString)
      assertEquals(f.editor.error, None)
      assert(f.host.querySelector("button[aria-label=Python]").asInstanceOf[dom.html.Button].disabled)
    } finally f.close()
  }
  test("switching a view uses its adapter without publishing a converted answer") {
    val source = ProgrammingStatePythonString("\nforward(12)\n")
    val f = new Fixture(source)
    try {
      f.editor.switchTabTo(Java)
      assertEquals(f.editor.selectedLanguage, Some(Java))
      assertEquals(f.tabs(Java).value.now(), source.toJava)
      assertEquals(f.state.now(), source)
      assertEquals(f.tabs(Python).closes.now(), 1)
      assert(f.tabs(Java).opens.now() > 0)
    } finally f.close()
  }
  test("failed conversion retains the view, source and visible error without closing modules") {
    val source = ProgrammingStateJavaString("class Drawing {")
    val closes = Var(0)
    val module = new EvaEditorModule {
      override def onFullscreenClose(): Unit = closes.update(_ + 1)
    }
    val f = new Fixture(source, modules = List(module))
    try {
      f.editor.switchTabTo(Python)
      assertEquals(f.editor.selectedLanguage, Some(Java))
      assertEquals(f.state.now(), source)
      assert(f.editor.error.nonEmpty)
      assert(f.host.querySelector("[role=alert]") != null)
      assertEquals(closes.now(), 0)
    } finally f.close()
  }
  test("active edits persist once, round-trip edit cycles are retained, inactive edits are ignored") {
    val source = ProgrammingStatePythonString("forward(1)")
    val f = new Fixture(source)
    val stored = Var(Vector.empty[ProgrammingState])
    val owner = new com.raquo.airstream.ownership.ManualOwner
    f.state.signal.changes.foreach(next => stored.update(_ :+ next))(using owner)
    val initialRenders = f.tabs(Python).renders.now()
    try {
      f.tabs(Java).value.set(ProgrammingStateJavaString("forward(99);"))
      assertEquals(stored.now(), Vector.empty)
      f.tabs(Python).value.set(ProgrammingStatePythonString("forward(2)"))
      f.tabs(Python).value.set(source)
      assertEquals(stored.now(), Vector(ProgrammingStatePythonString("forward(2)"), source))
      assertEquals(f.state.now(), source)
      assertEquals(f.tabs(Python).renders.now(), initialRenders, "typing must not remount the editor")
    } finally { owner.killSubscriptions(); f.close() }
  }
  test("external restores select and update their tab, including incomplete drafts") {
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"))
    val restored = ProgrammingStateJavaString("\r\nclass Restored {\t")
    try {
      f.state.set(restored)
      assertEquals(f.editor.selectedLanguage, Some(Java))
      assertEquals(f.tabs(Java).value.now(), restored)
      assertEquals(f.state.now(), restored)
    } finally f.close()
  }
  test("restoring another language closes the old view and rejects its closing edits") {
    val f = new Fixture(ProgrammingStateJavaString("forward(1);"))
    val restored = ProgrammingStatePythonString("forward(2)")
    f.tabs(Java).onClose.set(() => f.tabs(Java).value.set(ProgrammingStateJavaString("forward(99);")))
    try {
      f.state.set(restored)
      assertEquals(f.editor.selectedLanguage, Some(Python))
      assertEquals(f.state.now(), restored)
      assertEquals(f.tabs(Java).closes.now(), 1)
    } finally f.close()
  }
  test("headless modules bind and clean up without adding layout cells") {
    val bound = Var(Option.empty[EvaEditorModule.Context])
    val closes = Var(0)
    val module = new EvaEditorModule {
      override def bind(context: EvaEditorModule.Context): Unit = bound.set(Some(context))
      override def onFullscreenClose(): Unit = closes.update(_ + 1)
    }
    val source = ProgrammingStatePythonString("forward(1)")
    val f = new Fixture(source, modules = List(module))
    try {
      assert(bound.now().get.state eq f.state)
      assertEquals(bound.now().get.captureSource(), source)
      assertEquals(f.host.querySelector(".eva-editor").children.length, 2)
    } finally f.close()
    assertEquals(closes.now(), 1)
  }
  test("fullscreen close and unmount clean up a module once") {
    val closes = Var(0)
    val module = new EvaEditorModule {
      override def onFullscreenClose(): Unit = closes.update(_ + 1)
    }
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"), modules = List(module))
    f.editor.onFullscreenClose()
    f.close()
    assertEquals(closes.now(), 1)
  }
  test("disabled tabs and empty configurations retain the source without constructing an editor") {
    val source = ProgrammingStatePythonString("unfinished(")
    val f = new Fixture(source, Nil)
    try {
      f.editor.switchTabTo(Java)
      assertEquals(f.editor.selectedLanguage, None)
      assertEquals(f.state.now(), source)
      assertEquals(f.host.querySelectorAll(".eva-editor__tabs button").length, 0)
    } finally f.close()
  }
  test("preparing validates without installing or emitting edits; installing suppresses echoes") {
    val tab = new TestTab(Java)
    val stored = Var(Vector.empty[ProgrammingState])
    val owner = new com.raquo.airstream.ownership.ManualOwner
    tab.edits.foreach(value => stored.update(_ :+ value))(using owner)
    val initial = tab.value.now()
    val source = ProgrammingStatePythonString("forward(1)")
    try {
      val install = tab.prepare(source).get
      assertEquals(tab.value.now(), initial)
      assertEquals(stored.now(), Vector.empty)
      install()
      assertEquals(tab.value.now(), source.toJava)
      assertEquals(stored.now(), Vector.empty)
      tab.value.set(ProgrammingStateJavaString("forward(2);"))
      tab.value.set(source.toJava)
      assertEquals(stored.now(), Vector(ProgrammingStateJavaString("forward(2);"), source.toJava))
      assert(new TestTab(Python).prepare(ProgrammingStateJavaString("class Unfinished {")).isFailure)
      assertEquals(tab.value.now(), source.toJava)
    } finally owner.killSubscriptions()
  }
  test("closing prevents navigation until the editor is reopened") {
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"))
    try {
      f.editor.onFullscreenClose()
      f.editor.switchTabTo(Java)
      assertEquals(f.editor.selectedLanguage, Some(Python))
      f.editor.onFullscreenOpen()
      f.editor.switchTabTo(Java)
      assertEquals(f.editor.selectedLanguage, Some(Java))
    } finally f.close()
  }
  test("closing modules can restore a source and prevent a prepared switch committing") {
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"))
    val restored = ProgrammingStatePythonString("forward(9)")
    f.tabs(Python).onClose.set(() => f.state.set(restored))
    try {
      f.editor.switchTabTo(Java)
      assertEquals(f.editor.selectedLanguage, Some(Python))
      assertEquals(f.state.now(), restored)
    } finally f.close()
  }
  test("capture flushes the active adapter before navigation") {
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"))
    f.tabs(Python).onCapture.set(() => f.tabs(Python).value.set(ProgrammingStatePythonString("forward(8)")))
    try {
      f.editor.switchTabTo(Java)
      assertEquals(f.tabs(Java).value.now(), ProgrammingStatePythonString("forward(8)").toJava)
      assertEquals(f.state.now(), ProgrammingStatePythonString("forward(8)"))
    } finally f.close()
  }
}
