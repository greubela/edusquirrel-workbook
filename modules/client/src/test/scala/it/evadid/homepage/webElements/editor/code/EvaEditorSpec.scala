package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.distribution.clients.{ExecutionClient, RoutedExecutionClient}
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.*
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection.DomElementSingle
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.RenderingWorkbookElementLine
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.abstractions.{FeedbackEntity, WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.abstractions.scaffolding.{AdditionalInfoLlmScaffolding, ScaffoldingResult, ScaffoldingStatus, WorkbookInteractionScaffolder}
import it.evadid.workbook.meta.ExecutionContextInformation
import it.evadid.workbook.interaction.variable.{InteractionVariableGradingHistory, InteractionVariableScaffoldingHistory}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingHintScaffolder
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout
import scala.util.Try
import upickle.default.*

class EvaEditorSpec extends EditorDomSuite {
  private def executionContext(clients: List[ExecutionClient], resolver: LanguageMapIdResolver): ExecutionContextInformation = new ExecutionContextInformation {
    override val availableExecutionClients = clients
    override val languageMapIdResolver = Some(resolver)
    override val syncControl = None
    override def getCurrentLanguage = resolver.languageFuture().value.flatMap(_.toOption).getOrElse(English)
    override val futureExecutionContext = queue
  }

  private val specLabels = EvaEditorLabels(
    unavailable = Val("_"),
    languages = Val("_"),
    switchFailed = Val("_"),
    loading = Val("_")
  )
  private val logger = Logger.withNameAndPrefixes(Some("EvaEditorSpec"), PrintToStdLogger.printNothing)

  private class Fixture(source: ProgrammingState, languages: List[ProgrammingLanguage] = List(SnapLanguage, Python, Java)) {
    val state = Var(source)
    val created = Var(Vector.empty[ProgrammingLanguage])
    val mounted = Var(Set.empty[ProgrammingLanguage])
    val observed = Var(Vector.empty[(ProgrammingLanguage, ProgrammingState)])
    val maximum = Var(0)
    val choices = languages.distinct.map { lang => new EvaEditorTab {
      override val language = lang
      override val label = lang.toString
      override def create(bound: Var[ProgrammingState], appearance: EditorAppearance,
          marker: Signal[Option[it.evadid.vm.code.errors.ProgrammingConversionError]], loading: Signal[String]): HtmlAppElement = {
        assert(bound eq state)
        created.update(_ :+ lang)
        HtmlAppElement(div(
          onMountCallback { _ => mounted.update(_ + lang); maximum.update(_ max mounted.now().size) },
          onUnmountCallback(_ => mounted.update(_ - lang)),
          bound.signal --> (value => observed.update(_ :+ (lang -> value)))
        ))
      }
    }}
    val editor = EvaEditor(logger, state, Nil, EvaEditorConfig(enabledLanguages = languages), Some(choices), labels = specLabels)
    val close = mount(editor.getDomElement())
  }

  test("startup creates only the matching editor; changes update it without creating another") {
    val source = ProgrammingStateJavaString("class Unfinished {")
    val f = new Fixture(source)
    try {
      assertEquals(f.created.now(), Vector(Java))
      assertEquals(f.mounted.now(), Set[ProgrammingLanguage](Java))
      val restored = ProgrammingStateJavaString("class Restored {\n")
      f.state.set(restored)
      assertEquals(f.created.now(), Vector(Java))
      assertEquals(f.observed.now().last, Java -> restored)
    } finally f.close()
    assertEquals(f.mounted.now(), Set.empty)
  }
  test("switching converts the central answer and replaces the editor; returning creates a fresh one") {
    val source = ProgrammingStatePythonString("forward(12)")
    val f = new Fixture(source)
    f.editor.switchTabTo(Java).flatMap { _ =>
      assertEquals(f.state.now(), source.toJava)
      assertEquals(f.created.now(), Vector(Python, Java))
      assertEquals(f.mounted.now(), Set[ProgrammingLanguage](Java))
      f.editor.switchTabTo(Python)
    }.map { _ =>
      assertEquals(f.created.now(), Vector(Python, Java, Python))
      assertEquals(f.mounted.now(), Set[ProgrammingLanguage](Python))
      assert(f.maximum.now() <= 1)
    }.andThen { case _ => f.close() }
  }
  test("all central changes are observed without an editor-specific edit channel") {
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"))
    try {
      f.state.set(ProgrammingStatePythonString("forward(2)"))
      f.state.set(ProgrammingStatePythonString("forward(3)"))
      assertEquals(f.observed.now().map(_._2), Vector(ProgrammingStatePythonString("forward(1)"),
        ProgrammingStatePythonString("forward(2)"), ProgrammingStatePythonString("forward(3)")))
    } finally f.close()
  }
  test("failed conversion retains the draft and mounted editor") {
    val source = ProgrammingStateJavaString("class Unfinished {")
    val f = new Fixture(source)
    f.editor.switchTabTo(Python).map { _ =>
      assertEquals(f.state.now(), source)
      assertEquals(f.created.now(), Vector(Java))
      assertEquals(f.mounted.now(), Set[ProgrammingLanguage](Java))
      assert(f.editor.error.nonEmpty)
    }.andThen { case _ => f.close() }
  }
  test("external language restores replace the active editor and release its subscription") {
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"))
    try {
      f.state.set(ProgrammingStateJavaString("unfinished class"))
      val count = f.observed.now().count(_._1 == Python)
      f.state.set(ProgrammingStateJavaString("restored class"))
      assertEquals(f.created.now(), Vector(Python, Java))
      assertEquals(f.observed.now().count(_._1 == Python), count)
      assertEquals(f.mounted.now(), Set[ProgrammingLanguage](Java))
    } finally f.close()
  }
  test("the latest requested conversion wins") {
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"))
    val first = f.editor.switchTabTo(Java)
    val last = f.editor.switchTabTo(SnapLanguage)
    Future.sequence(List(first, last)).map { _ =>
      assertEquals(f.editor.selectedLanguage, Some(SnapLanguage))
      assertEquals(f.created.now(), Vector(Python, SnapLanguage))
    }.andThen { case _ => f.close() }
  }
  test("state changes and unmount cancel pending conversions, including A-to-B-to-A") {
    val original = ProgrammingStatePythonString("forward(1)")
    val f = new Fixture(original)
    val pending = f.editor.switchTabTo(Java)
    f.state.set(ProgrammingStatePythonString("forward(2)"))
    f.state.set(original)
    pending.flatMap { _ =>
      assertEquals(f.created.now(), Vector(Python))
      val closed = f.editor.switchTabTo(Java)
      f.close()
      closed
    }.map(_ => assertEquals(f.state.now(), original))
  }
  test("empty configurations do not create any editor") {
    val f = new Fixture(ProgrammingStatePythonString("unfinished("), Nil)
    try assertEquals(f.created.now(), Vector.empty) finally f.close()
  }

  private def tabsInDom: Seq[dom.Element] = {
    val nodes = dom.document.querySelectorAll(".eva-editor__tabs button")
    (0 until nodes.length).map(index => nodes.item(index).asInstanceOf[dom.Element])
  }

  private def tab(label: String): dom.Element =
    tabsInDom.find(_.getAttribute("aria-label") == label).getOrElse(throw new AssertionError(s"Missing tab $label"))

  private def click(element: dom.Element): Unit = element.asInstanceOf[js.Dynamic].click()

  test("a blocked tab names the conversion failure and stays on the draft") {
    val source = ProgrammingStateJavaString("class Unfinished {")
    val f = new Fixture(source)
    tabsInDom.filterNot(_.getAttribute("aria-selected") == "true").foreach(
      _.asInstanceOf[dom.html.Element].focus())
    laterOnPage(50).flatMap { _ =>
      val blocked = tabsInDom.filter { button =>
        button.getAttribute("aria-disabled") == "true" && button.getAttribute("title") != button.getAttribute("aria-label")
      }
      assert(blocked.nonEmpty)
      assert(blocked.forall(_.getAttribute("aria-pressed") == null))
      click(blocked.head)
      laterOnPage(50)
    }.map { _ =>
      assertEquals(f.state.now(), source)
      assert(f.editor.error.nonEmpty)
      assert(dom.document.querySelector(".eva-editor__error") != null)
    }.andThen { case _ => f.close() }
  }

  test("arrow keys move focus between tabs without switching the draft") {
    val source = ProgrammingStatePythonString("forward(1)")
    val f = new Fixture(source)
    try {
      val current = tab("Python")
      assertEquals(current.getAttribute("role"), "tab")
      assertEquals(current.getAttribute("aria-selected"), "true")
      assertEquals(dom.document.querySelector(".eva-editor__tabs").getAttribute("role"), "tablist")
      current.asInstanceOf[js.Dynamic].focus()
      val event = js.Dynamic.newInstance(dom.window.asInstanceOf[js.Dynamic].KeyboardEvent)(
        "keydown", js.Dynamic.literal(key = "ArrowRight", bubbles = true))
      current.asInstanceOf[js.Dynamic].dispatchEvent(event)
      assertEquals(dom.document.activeElement.asInstanceOf[dom.Element].getAttribute("aria-label"), "Java")
      assertEquals(f.state.now(), source)
      assertEquals(tab("Python").getAttribute("aria-selected"), "true")
    } finally f.close()
  }

  private def laterOnPage(milliseconds: Int): Future[Unit] = {
    val completed = Promise[Unit]()
    dom.window.asInstanceOf[js.Dynamic].setTimeout(() => completed.success(()), milliseconds)
    completed.future
  }

  test("the editor theme follows data-theme on the page") {
    dom.document.documentElement.setAttribute("data-theme", "light")
    val f = new Fixture(ProgrammingStatePythonString("forward(1)"))
    laterOnPage(50).map { _ =>
      assertEquals(dom.document.querySelector(".eva-editor").getAttribute("data-editor-theme"), "light")
      assert(dom.document.querySelector(".editor-appearance__toggle") == null)
      dom.document.documentElement.setAttribute("data-theme", "dark")
      laterOnPage(50)
    }.flatten.map { _ =>
      assertEquals(dom.document.querySelector(".eva-editor").getAttribute("data-editor-theme"), "dark")
    }.andThen { case _ =>
      dom.document.documentElement.removeAttribute("data-theme")
      f.close()
    }
  }
}
