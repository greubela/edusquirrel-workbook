package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.*
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite
import org.scalajs.dom
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
