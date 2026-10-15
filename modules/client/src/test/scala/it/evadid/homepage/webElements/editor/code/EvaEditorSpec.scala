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
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class EvaEditorSpec extends FunSuite {
  private val logger = Logger.withNameAndPrefixes(Some("EvaEditorSpec"), PrintToStdLogger.printNothing)
  private val globals = js.Dynamic.global.globalThis
  private val names = List("window", "document", "Element", "Node")
  private var previous = List.empty[(String, js.Dynamic)]
  private lazy val window = {
    val req = js.Dynamic.global.selectDynamic("require")
    val root = js.Dynamic.global.process.cwd().asInstanceOf[String]
    js.Dynamic.newInstance(req(root + "/node_modules/jsdom").JSDOM)("<html><body></body></html>").window
  }
  override def beforeAll(): Unit = {
    previous = names.map(name => name -> js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, name))
    names.foreach(name => globals.updateDynamic(name)(if name == "window" then window else window.selectDynamic(name)))
  }
  override def afterAll(): Unit = {
    previous.foreach { (name, descriptor) =>
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
    var requests = Vector.empty[ProgrammingState]
    var conversion: ProgrammingState => Future[ProgrammingState] = source => Future(source.toLanguage(language))
    var renders = 0
    var captures = 0
    var opens = 0
    var closes = 0
    var onCapture: () => Unit = () => ()
    var onClose: () => Unit = () => ()
    override protected def convertAsync(source: ProgrammingState): Future[ProgrammingState] = {
      requests :+= source
      conversion(source)
    }
    override def toProgram(value: ProgrammingState): ProgrammingState = value
    override def fromProgram(value: ProgrammingState): ProgrammingState = sourceFor(value)
    private def sourceFor(value: ProgrammingState): ProgrammingState = value.toLanguage(language)
    override def captureSource(): ProgrammingState = { captures += 1; onCapture(); value.now() }
    override def getDomElement(): Element = { renders += 1; div(cls := "test-tab", language.toString) }
    override def onFullscreenOpen(): Unit = opens += 1
    override def onFullscreenClose(): Unit = { closes += 1; onClose() }
  }
  private class Fixture(source: ProgrammingState, languages: List[ProgrammingLanguage] = List(SnapLanguage, Python, Java),
      modules: Seq[EvaEditorModule] = Nil, configure: Map[ProgrammingLanguage, TestTab] => Unit = _ => ()) {
    val state = Var(source)
    val tabs = languages.distinct.map(l => l -> new TestTab(l)).toMap
    configure(tabs)
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
    } finally f.close()
  }
  test("switching a view uses its adapter without publishing a converted answer") {
    val source = ProgrammingStatePythonString("\nforward(12)\n")
    val f = new Fixture(source)
    f.editor.switchTabTo(Java).map { _ =>
      assertEquals(f.editor.selectedLanguage, Some(Java))
      assertEquals(f.tabs(Java).value.now(), source.toJava)
      assertEquals(f.state.now(), source)
      assertEquals(f.tabs(Python).closes, 1)
      assert(f.tabs(Java).opens > 0)
    }.andThen { case _ => f.close() }
  }
  test("failed conversion retains the view, source and visible error without closing modules") {
    val source = ProgrammingStateJavaString("class Drawing {")
    var closes = 0
    val module = new EvaEditorModule {
      override def onFullscreenClose(): Unit = closes += 1
    }
    val f = new Fixture(source, modules = List(module))
    f.editor.switchTabTo(Python).map { _ =>
      assertEquals(f.editor.selectedLanguage, Some(Java))
      assertEquals(f.state.now(), source)
      assert(f.editor.error.nonEmpty)
      assert(f.host.querySelector("[role=alert]") != null)
      assertEquals(closes, 0)
    }.andThen { case _ => f.close() }
  }
  test("active edits persist once, round-trip edit cycles are retained, inactive edits are ignored") {
    val source = ProgrammingStatePythonString("forward(1)")
    val f = new Fixture(source)
    var stored = Vector.empty[ProgrammingState]
    val owner = new com.raquo.airstream.ownership.ManualOwner
    f.state.signal.changes.foreach(next => stored :+= next)(using owner)
    val initialRenders = f.tabs(Python).renders
    try {
      f.tabs(Java).value.set(ProgrammingStateJavaString("forward(99);"))
      assertEquals(stored, Vector.empty)
      f.tabs(Python).value.set(ProgrammingStatePythonString("forward(2)"))
      f.tabs(Python).value.set(source)
      assertEquals(stored, Vector(ProgrammingStatePythonString("forward(2)"), source))
      assertEquals(f.state.now(), source)
      assertEquals(f.tabs(Python).renders, initialRenders, "typing must not remount the editor")
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
    f.tabs(Java).onClose = () => f.tabs(Java).value.set(ProgrammingStateJavaString("forward(99);"))
    try {
      f.state.set(restored)
      assertEquals(f.editor.selectedLanguage, Some(Python))
      assertEquals(f.state.now(), restored)
      assertEquals(f.tabs(Java).closes, 1)
    } finally f.close()
  }
  test("headless modules bind and clean up without adding layout cells") {
    var bound = Option.empty[EvaEditorModule.Context]
    var closes = 0
    val module = new EvaEditorModule {
      override def bind(context: EvaEditorModule.Context): Unit = bound = Some(context)
      override def onFullscreenClose(): Unit = closes += 1
    }
    val source = ProgrammingStatePythonString("forward(1)")
    val f = new Fixture(source, modules = List(module))
    try {
      assert(bound.get.state eq f.state)
      assertEquals(bound.get.captureSource(), source)
      assertEquals(f.host.querySelector(".eva-editor").children.length, 2)
    } finally f.close()
    assertEquals(closes, 1)
  }
  test("fullscreen close and unmount clean up a module once") {
    var closes = 0
    val module = new EvaEditorModule {
      override def onFullscreenClose(): Unit = closes += 1
    }
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"), modules = List(module))
    f.editor.onFullscreenClose()
    f.close()
    assertEquals(closes, 1)
  }
  test("disabled tabs and empty configurations retain the source without constructing an editor") {
    val source = ProgrammingStatePythonString("unfinished(")
    val f = new Fixture(source, Nil)
    f.editor.switchTabTo(Java).map { _ =>
      assertEquals(f.editor.selectedLanguage, None)
      assertEquals(f.state.now(), source)
      assertEquals(f.host.querySelectorAll(".eva-editor__tabs button").length, 0)
    }.andThen { case _ => f.close() }
  }
  test("conversion caching shares the current draft and releases previous draft entries") {
    val tab = new TestTab(Java)
    val first = ProgrammingStatePythonString("forward(1)")
    val second = ProgrammingStatePythonString("forward(2)")
    val original = tab.prepare(first)
    val shared = tab.prepare(first)
    assertEquals(tab.requests, Vector(first))
    val changed = tab.prepare(second)
    val returned = tab.prepare(first)
    assertEquals(tab.requests, Vector(first, second, first))
    Future.sequence(List(original, shared, changed, returned)).map(_ => ())
  }
  test("the latest tab request wins and repeated pending conversions share the adapter cache") {
    val java = Promise[ProgrammingState]()
    val snap = Promise[ProgrammingState]()
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"), configure = tabs => {
      tabs(Java).conversion = _ => java.future
      tabs(SnapLanguage).conversion = _ => snap.future
    })
    val first = f.editor.switchTabTo(Java)
    val repeat = f.editor.switchTabTo(Java)
    val last = f.editor.switchTabTo(SnapLanguage)
    assertEquals(f.tabs(Java).requests.size, 1)
    java.success(ProgrammingStateJavaString("forward(1);"))
    snap.success(ProgrammingStateSnapXml.empty)
    Future.sequence(List(first, repeat, last)).map { _ =>
      assertEquals(f.editor.selectedLanguage, Some(SnapLanguage))
      assertEquals(f.state.now(), ProgrammingStatePythonString("forward(1)"))
    }.andThen { case _ => f.close() }
  }
  test("reselecting the visible tab invalidates a pending switch") {
    val source = ProgrammingStatePythonString("forward(1)")
    val conversion = Promise[ProgrammingState]()
    val f = new Fixture(source, configure = tabs => tabs(Java).conversion = _ => conversion.future)
    val pending = f.editor.switchTabTo(Java)
    f.editor.switchTabTo(Python)
    conversion.success(source.toJava)
    pending.map { _ => assertEquals(f.editor.selectedLanguage, Some(Python)) }
      .andThen { case _ => f.close() }
  }
  test("an edit A-to-B-to-A invalidates a pending conversion") {
    val source = ProgrammingStatePythonString("forward(1)")
    val conversion = Promise[ProgrammingState]()
    val f = new Fixture(source, configure = tabs => tabs(Java).conversion = _ => conversion.future)
    val pending = f.editor.switchTabTo(Java)
    f.state.set(ProgrammingStatePythonString("forward(2)"))
    f.state.set(source)
    conversion.success(source.toJava)
    pending.map { _ =>
      assertEquals(f.editor.selectedLanguage, Some(Python))
      assertEquals(f.state.now(), source)
    }.andThen { case _ => f.close() }
  }
  test("closing invalidates late results and reopening may reuse the prepared conversion") {
    val source = ProgrammingStatePythonString("forward(1)")
    val conversion = Promise[ProgrammingState]()
    val f = new Fixture(source, configure = tabs => tabs(Java).conversion = _ => conversion.future)
    val pending = f.editor.switchTabTo(Java)
    f.editor.onFullscreenClose()
    conversion.success(source.toJava)
    pending.flatMap { _ =>
      assertEquals(f.editor.selectedLanguage, Some(Python))
      f.editor.onFullscreenOpen()
      f.editor.switchTabTo(Java)
    }.map { _ => assertEquals(f.editor.selectedLanguage, Some(Java)) }
      .andThen { case _ => f.close() }
  }
  test("closing modules can restore a source and prevent a prepared switch committing") {
    val source = ProgrammingStatePythonString("forward(1)")
    val f = new Fixture(source)
    val restored = ProgrammingStatePythonString("forward(9)")
    f.tabs(Python).onClose = () => f.state.set(restored)
    f.editor.switchTabTo(Java).map { _ =>
      assertEquals(f.editor.selectedLanguage, Some(Python))
      assertEquals(f.state.now(), restored)
    }.andThen { case _ => f.close() }
  }
  test("capture flushes the active adapter before navigation") {
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"))
    f.tabs(Python).onCapture = () => f.tabs(Python).value.set(ProgrammingStatePythonString("forward(8)"))
    f.editor.switchTabTo(Java).map { _ =>
      assertEquals(f.tabs(Java).value.now(), ProgrammingStatePythonString("forward(8)").toJava)
      assertEquals(f.state.now(), ProgrammingStatePythonString("forward(8)"))
    }.andThen { case _ => f.close() }
  }
}
