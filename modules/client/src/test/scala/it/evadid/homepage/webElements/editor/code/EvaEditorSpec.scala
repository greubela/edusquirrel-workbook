package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.*
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection.DomElementSingle
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.RenderingWorkbookElementLine
import it.evadid.homepage.workbook.htmlRenderer.controlElements.{InteractionInfoPanel, InteractionScaffoldingPanel}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.abstractions.{FeedbackEntity, WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, WorkbookInteractionScaffolder}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout
import scala.util.Try
import upickle.default.*

class EvaEditorSpec extends EditorDomSuite {
  private val logger = Logger.withNameAndPrefixes(Some("EvaEditorSpec"), PrintToStdLogger.printNothing)

  private type HintResult = ScaffoldingResult[String, String]
  private class ScaffoldingFixture(timeoutMillis: Int) {
    val source = Var("original source\r\n\t ")
    val language = Var[HumanLanguage](English)
    val context = new EventBus[Unit]
    val requests = Var(Vector.empty[(String, Promise[HintResult])])
    val accepted = Var(Vector.empty[HintResult])
    val synchronousFailure = Var(Option.empty[Throwable])
    private val release = Var(Option.empty[() => Unit])
    private def label(english: String, german: String): Signal[String] =
      language.signal.map(current => if current == German then german else english)
    val panel = new InteractionScaffoldingPanel[String, String](
      () => source.now(), source.signal.changes, language.signal, context.events,
      value => {
        val pending = Promise[HintResult]()
        requests.update(_ :+ (value -> pending))
        synchronousFailure.now().foreach(error => throw error)
        pending.future
      }, result => accepted.update(_ :+ result),
      InteractionScaffoldingPanel.Labels(label("Request", "Anfragen"), label("Loading", "Wird vorbereitet"),
        label("Dismiss", "Ausblenden"), label("Unavailable", "Nicht verfügbar"),
        label("Timed out", "Zeit überschritten"), label("Notice", "Hinweis")), timeoutMillis)
    val element = panel.getDomElement()
    def open(): Unit = {
      assert(release.now().isEmpty)
      release.set(Some(mount(element)))
    }
    def close(): Unit = {
      release.now().foreach(_())
      release.set(None)
    }
    def button: dom.html.Button = element.ref.querySelector(".interaction-scaffolding__request").asInstanceOf[dom.html.Button]
    def dismiss: dom.html.Button = element.ref.querySelector(".interaction-scaffolding__dismiss").asInstanceOf[dom.html.Button]
    def status: dom.Element = element.ref.querySelector("[role='status']")
    def text: String = status.textContent
    def answer(value: String = source.now(), text: String = "1. What changes here?", locale: HumanLanguage = language.now()): HintResult =
      ScaffoldingResult(value, LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(locale -> text)),
        FeedbackEntity.TestEntity(), None)
    open()
  }

  private def later(milliseconds: Int = 0): Future[Unit] = {
    val completed = Promise[Unit]()
    setTimeout(milliseconds.toDouble) { completed.success(()); () }
    completed.future
  }

  private def withScaffolding(timeoutMillis: Int = 60000)(body: ScaffoldingFixture => Future[Unit]): Future[Unit] = {
    val fixture = new ScaffoldingFixture(timeoutMillis)
    Try(body(fixture)).fold(Future.failed, identity).andThen { case _ => fixture.close() }
  }

  test("interaction rendering publishes a requested hint to its existing history without changing the answer") {
    assertEquals(fullInfo.current.userInfo, None)
    assertEquals(fullInfo.current.workbookInfo, None)
    assertEquals(fullInfo.signals.currentSyncDestinations.now(), Nil)
    val requested = Var(Vector.empty[String])
    val clients = Var(Vector.empty[List[ExecutionClient]])
    val pending = Promise[HintResult]()
    val scaffolder = new WorkbookInteractionScaffolder[String, String] {
      override def generateFeedback(state: String, availableExecutionClients: List[ExecutionClient]): Future[HintResult] = {
        requested.update(_ :+ state)
        clients.update(_ :+ availableExecutionClients)
        pending.future
      }
    }
    val interaction = new WorkbookInteractionElementFull[String, String, String] {
      override val elementId = "renderer-hint-test"
      override val defaultValue = "unfinished source\r\n\t "
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val scaffoldingElement = Some(scaffolder)
    }
    val variable = interaction.interactionVariable
    val before = variable.history
    val element = RenderingWorkbookElementLine(interaction, new DomElementSingle(div("Answer")), "").render
    val close = mount(element)
    later().flatMap { _ =>
      assertEquals(requested.now(), Vector.empty)
      assertEquals(clients.now(), Vector.empty)
      assertEquals(variable.history, before)
      assertEquals(element.ref.querySelector(".interaction-scaffolding"), null)
      element.ref.querySelector(".interaction-info__hint-toggle").asInstanceOf[dom.html.Button].click()
      assertEquals(requested.now(), Vector.empty)
      assert(element.ref.querySelector(".interaction-element-info").contains(element.ref.querySelector(".interaction-scaffolding")))
      element.ref.querySelector(".interaction-scaffolding__request").asInstanceOf[dom.html.Button].click()
      later()
    }.flatMap { _ =>
      assertEquals(requested.now(), Vector(variable.currentValue))
      assertEquals(clients.now(), Vector(List(fullInfo.defaults.backendExecutor)))
      val feedback = ScaffoldingResult[String, String](variable.currentValue, LanguageMap.universalMap("1. What changes here?"),
        FeedbackEntity.TestEntity(), None)
      pending.success(feedback)
      later().map { _ =>
        assertEquals(variable.history.scaffoldings, Map(feedback.forState -> feedback))
        assertEquals(variable.history.events, before.events)
        assertEquals(variable.history.gradings, before.gradings)
        assertEquals(variable.currentValue, interaction.defaultValue)
        assertEquals(element.ref.querySelector("[role='status']").textContent, "1. What changes here?")
        assertEquals(element.ref.querySelector(".status-correct"), null)
        element.ref.querySelector(".interaction-info__hint-close").asInstanceOf[dom.html.Button].click()
        assertEquals(element.ref.querySelector(".interaction-scaffolding"), null)
        assertEquals(dom.document.activeElement, element.ref.querySelector(".interaction-info__hint-toggle"))
      }
    }.andThen { case _ => close() }
  }

  test("interaction rendering omits hint controls when no scaffolder is configured") {
    val interaction = TextInteraction("renderer-without-hints")
    val element = RenderingWorkbookElementLine(interaction, new DomElementSingle(div("Answer")), "").render
    val close = mount(element)
    try {
      assertEquals(interaction.scaffoldingElement, None)
      assertEquals(element.ref.querySelector(".interaction-scaffolding"), null)
      assertEquals(element.ref.querySelector(".interaction-scaffolding__request"), null)
      assertEquals(element.ref.querySelector(".interaction-info__hint-toggle"), null)
      val standalone = new HtmlAppElement {
        override def dismissOnOutsideClick: Boolean = false
        override def getDomElement(): Element = div("Editor")
      }
      assert(InteractionInfoPanel.withEditor(interaction, standalone) eq standalone)
    } finally close()
  }

  test("fullscreen hints use the current Java answer and reject completions from a closed editor") {
    import ProgrammingState.ProgrammingState.ProgrammingStateJavaString
    import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
    type Result = ScaffoldingResult[ProgrammingState, String]
    val requests = Var(Vector.empty[(ProgrammingState, Promise[Result])])
    val scaffolder = new WorkbookInteractionScaffolder[ProgrammingState, String] {
      override def generateFeedback(source: ProgrammingState, availableExecutionClients: List[ExecutionClient]): Future[Result] = {
        val result = Promise[Result]()
        requests.update(_ :+ (source -> result))
        result.future
      }
    }
    val interaction = new WorkbookInteractionElementFull[ProgrammingState, String, String] {
      override val elementId = "fullscreen-java-hints"
      override val defaultValue: ProgrammingState =
        ProgrammingStateJavaString("class Drawing { public static void main(String[] args) { Turtle.forward(20); } }")
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val scaffoldingElement = Some(scaffolder)
    }
    val variable = interaction.interactionVariable
    val answer = variable.createBoundVarWithUpdateImportance(fullInfo.syncControl, MAJOR)
    val mounted = Var(0)
    val release = Var(Option.empty[() => Unit])
    def open(): Element = {
      val editor = new HtmlAppElement {
        override def dismissOnOutsideClick: Boolean = false
        override def getDomElement(): Element = div(
          onMountCallback(_ => mounted.update(_ + 1)),
          onUnmountCallback(_ => mounted.update(_ - 1)),
          pre(child.text <-- answer.signal.map(_.asInstanceOf[ProgrammingStateJavaString].code)))
      }
      val wrapped = InteractionInfoPanel.withEditor(interaction, editor)
      assert(!wrapped.dismissOnOutsideClick)
      val element = wrapped.getDomElement()
      release.set(Some(mount(element)))
      element
    }
    def close(): Unit = { release.now().foreach(_()); release.set(None) }
    def request(element: Element): Unit = {
      element.ref.querySelector(".interaction-info__hint-toggle").asInstanceOf[dom.html.Button].click()
      element.ref.querySelector(".interaction-scaffolding__request").asInstanceOf[dom.html.Button].click()
    }
    val first = open()
    later().flatMap { _ =>
      assertEquals(mounted.now(), 1)
      assertEquals(requests.now().size, 0)
      request(first)
      later()
    }.flatMap { _ =>
      assertEquals(requests.now().map(_._1), Vector(answer.now()))
      close()
      assertEquals(mounted.now(), 0)
      requests.now().head._2.success(ScaffoldingResult(requests.now().head._1,
        LanguageMap.universalMap("Old hint"), FeedbackEntity.TestEntity(), None))
      later()
    }.flatMap { _ =>
      assertEquals(variable.history.scaffoldings, Map.empty)
      val current: ProgrammingState = ProgrammingStateJavaString("class Drawing { public static void main(String[] args) { Turtle.forward(30); } }")
      answer.set(current)
      val second = open()
      assertEquals(mounted.now(), 1)
      assert(second.ref.querySelector("pre").textContent.contains("forward(30)"))
      request(second)
      later().flatMap { _ =>
        assertEquals(requests.now().map(_._1), Vector(interaction.defaultValue, current))
        val events = variable.history.events
        val feedback = ScaffoldingResult[ProgrammingState, String](current,
          LanguageMap.universalMap("Check the distance."), FeedbackEntity.TestEntity(), None)
        requests.now().last._2.success(feedback)
        later().map { _ =>
          assertEquals(variable.history.scaffoldings, Map(current -> feedback))
          assertEquals(variable.history.events, events)
          assertEquals(variable.currentValue, current)
          assertEquals(variable.history.gradings, Map.empty)
          assert(second.ref.querySelector(".interaction-element-info").contains(second.ref.querySelector(".interaction-scaffolding")))
        }
      }
    }.andThen { case _ => close() }
  }

  test("closing the info panel with Escape restores focus and rejects pending feedback") {
    val requested = Var(0)
    val pending = Promise[HintResult]()
    val interaction = new WorkbookInteractionElementFull[String, String, String] {
      override val elementId = "keyboard-hints"
      override val defaultValue = "answer"
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val scaffoldingElement = Some(new WorkbookInteractionScaffolder[String, String] {
        override def generateFeedback(source: String, availableExecutionClients: List[ExecutionClient]): Future[HintResult] = {
          requested.update(_ + 1)
          pending.future
        }
      })
    }
    val element = InteractionInfoPanel(interaction).getDomElement()
    val release = mount(element)
    val toggle = element.ref.querySelector(".interaction-info__hint-toggle").asInstanceOf[dom.html.Button]
    toggle.click()
    val request = element.ref.querySelector(".interaction-scaffolding__request").asInstanceOf[dom.html.Button]
    assertEquals(dom.document.activeElement, request)
    assertEquals(toggle.getAttribute("aria-expanded"), "true")
    request.click()
    later().flatMap { _ =>
      assertEquals(requested.now(), 1)
      val escape = js.Dynamic.newInstance(dom.window.asInstanceOf[js.Dynamic].KeyboardEvent)("keydown",
        js.Dynamic.literal(key = "Escape", bubbles = true, cancelable = true)).asInstanceOf[dom.KeyboardEvent]
      request.dispatchEvent(escape)
      assert(escape.defaultPrevented)
      assertEquals(element.ref.querySelector(".interaction-scaffolding"), null)
      assertEquals(toggle.getAttribute("aria-expanded"), "false")
      assertEquals(dom.document.activeElement, toggle)
      pending.success(ScaffoldingResult("answer", LanguageMap.universalMap("Old hint"), FeedbackEntity.TestEntity(), None))
      later().map { _ =>
        assertEquals(interaction.interactionVariable.history.scaffoldings, Map.empty)
        assertEquals(interaction.interactionVariable.currentValue, "answer")
      }
    }.andThen { case _ => release() }
  }

  test("manual hints do not run on mount, edits or language and context changes") {
    withScaffolding() { f =>
      f.source.set("edited source")
      f.language.set(German)
      f.context.writer.onNext(())
      later().map { _ =>
        assertEquals(f.requests.now(), Vector.empty)
        assertEquals(f.accepted.now(), Vector.empty)
        assertEquals(f.button.textContent, "Anfragen")
        assertEquals(f.element.ref.querySelector(".interaction-scaffolding__notice").textContent, "Hinweis")
        assertEquals(f.text, "")
        assert(!f.button.disabled)
        assert(f.dismiss.hasAttribute("hidden"))
      }
    }
  }

  test("manual hints queue duplicate clicks once and render accepted numbered text without HTML") {
    withScaffolding() { f =>
      val original = f.source.now()
      val trigger = new EventBus[Unit]
      val owner = new ManualOwner
      trigger.events.foreach { _ => f.button.click(); f.button.click() }(using owner)
      trigger.writer.onNext(())
      owner.killSubscriptions()
      assertEquals(f.requests.now().size, 0)
      later().flatMap { _ =>
        assertEquals(f.requests.now().map(_._1), Vector(original))
        assert(f.button.disabled)
        assertEquals(f.status.getAttribute("aria-busy"), "true")
        assertEquals(f.text, "Loading")
        assert(!f.dismiss.hasAttribute("hidden"))
        f.button.click()
        val text = "1. What does <img src=x onerror=alert(1)> mean?\n2. Why is <script>alert(1)</script> here?"
        val feedback = f.answer(text = text)
        f.requests.now().head._2.success(feedback)
        later().map { _ =>
          assertEquals(f.requests.now().size, 1)
          assertEquals(f.accepted.now(), Vector(feedback))
          assertEquals(f.text, text)
          assertEquals(f.element.ref.querySelector("img,script"), null)
          assertEquals(f.source.now(), original)
          assertEquals(f.status.getAttribute("aria-busy"), "false")
          assert(!f.button.disabled)
        }
      }
    }
  }

  List("state", "locale").foreach { mismatch =>
    test(s"manual hints reject a response for another $mismatch without publishing it") {
      withScaffolding() { f =>
        f.button.click()
        later().flatMap { _ =>
          val feedback = if mismatch == "state" then f.answer(value = "another source") else f.answer(locale = German)
          f.requests.now().head._2.success(feedback)
          later().map { _ =>
            assertEquals(f.text, "Unavailable")
            assertEquals(f.accepted.now(), Vector.empty)
            assert(!f.button.disabled)
            assertEquals(f.source.now(), "original source\r\n\t ")
          }
        }
      }
    }
  }

  test("manual hints hide synchronous and asynchronous failure details and permit retry") {
    withScaffolding() { f =>
      val secret = "private source and credential"
      f.synchronousFailure.set(Some(IllegalStateException(secret)))
      f.button.click()
      later().flatMap { _ =>
        assertEquals(f.text, "Unavailable")
        assert(!f.element.ref.textContent.contains(secret))
        f.synchronousFailure.set(None)
        f.button.click()
        later()
      }.flatMap { _ =>
        assertEquals(f.requests.now().size, 2)
        f.requests.now().last._2.failure(IllegalArgumentException(secret))
        later()
      }.flatMap { _ =>
        assertEquals(f.text, "Unavailable")
        assert(!f.element.ref.textContent.contains(secret))
        assertEquals(f.accepted.now(), Vector.empty)
        f.button.click()
        later()
      }.flatMap { _ =>
        val feedback = f.answer()
        f.requests.now().last._2.success(feedback)
        later().map { _ =>
          assertEquals(f.accepted.now(), Vector(feedback))
          assertEquals(f.text, feedback.scaffoldingResult.getInLanguage(English))
          assert(!f.element.ref.textContent.contains(secret))
        }
      }
    }
  }

  List("source", "language", "context").foreach { changed =>
    test(s"manual hints discard a late answer after $changed changes even when restored") {
      withScaffolding() { f =>
        val original = f.source.now()
        f.button.click()
        later().flatMap { _ =>
          changed match {
            case "source" => f.source.set("changed"); f.source.set(original)
            case "language" => f.language.set(German); f.language.set(English)
            case _ => f.context.writer.onNext(())
          }
          f.requests.now().head._2.success(f.answer())
          later().map { _ =>
            assertEquals(f.accepted.now(), Vector.empty)
            assertEquals(f.text, "")
            assertEquals(f.source.now(), original)
            assert(!f.button.disabled)
          }
        }
      }
    }
  }

  List("source", "language", "context", "remount").foreach { changed =>
    test(s"manual hints do not dispatch a queued request invalidated by $changed changes") {
      withScaffolding() { f =>
        f.button.click()
        changed match {
          case "source" => val original = f.source.now(); f.source.set("changed"); f.source.set(original)
          case "language" => f.language.set(German); f.language.set(English)
          case "context" => f.context.writer.onNext(())
          case _ => f.close(); f.open()
        }
        later().map { _ =>
          assertEquals(f.requests.now(), Vector.empty)
          assertEquals(f.accepted.now(), Vector.empty)
          assertEquals(f.text, "")
        }
      }
    }
  }

  List("dismiss", "remount").foreach { action =>
    test(s"manual hints ignore an old completion after $action while a new request is pending") {
      withScaffolding() { f =>
        f.button.click()
        later().flatMap { _ =>
          if action == "dismiss" then f.dismiss.click() else { f.close(); f.open() }
          f.button.click()
          later()
        }.flatMap { _ =>
          assertEquals(f.requests.now().size, 2)
          f.requests.now().head._2.success(f.answer(text = "1. Old question?"))
          later()
        }.flatMap { _ =>
          assertEquals(f.text, "Loading")
          assertEquals(f.accepted.now(), Vector.empty)
          val feedback = f.answer(text = "1. Current question?")
          f.requests.now().last._2.success(feedback)
          later().map { _ =>
            assertEquals(f.accepted.now(), Vector(feedback))
            assertEquals(f.text, "1. Current question?")
          }
        }
      }
    }
  }

  test("manual hints time out locally and a retry rejects the old completion") {
    withScaffolding(10) { f =>
      f.button.click()
      later(30).flatMap { _ =>
        assertEquals(f.requests.now().size, 1)
        assertEquals(f.text, "Timed out")
        assert(!f.button.disabled)
        assertEquals(f.accepted.now(), Vector.empty)
        f.button.click()
        later()
      }.flatMap { _ =>
        assertEquals(f.requests.now().size, 2)
        val feedback = f.answer(text = "1. New question?")
        f.requests.now().head._2.success(f.answer(text = "1. Old question?"))
        f.requests.now().last._2.success(feedback)
        later().map { _ =>
          assertEquals(f.accepted.now(), Vector(feedback))
          assertEquals(f.text, "1. New question?")
        }
      }
    }
  }

  test("manual hints retain a pending request across identical answer echoes") {
    withScaffolding() { f =>
      f.button.click()
      later().flatMap { _ =>
        f.source.set(f.source.now())
        assert(f.button.disabled)
        assertEquals(f.text, "Loading")
        val feedback = f.answer()
        f.requests.now().head._2.success(feedback)
        later().map { _ =>
          assertEquals(f.accepted.now(), Vector(feedback))
          assertEquals(f.requests.now().size, 1)
        }
      }
    }
  }

  private class Fixture(source: ProgrammingState, languages: List[ProgrammingLanguage] = List(SnapLanguage, Python, Java)) {
    val state = Var(source)
    val created = Var(Vector.empty[ProgrammingLanguage])
    val mounted = Var(Set.empty[ProgrammingLanguage])
    val observed = Var(Vector.empty[(ProgrammingLanguage, ProgrammingState)])
    val maximum = Var(0)
    val choices = languages.distinct.map { lang => new EvaEditorTab {
      override val language = lang
      override val label = lang.toString
      override def create(bound: Var[ProgrammingState], appearance: EditorAppearance): HtmlAppElement = {
        assert(bound eq state)
        created.update(_ :+ lang)
        HtmlAppElement(div(
          onMountCallback { _ => mounted.update(_ + lang); maximum.update(_ max mounted.now().size) },
          onUnmountCallback(_ => mounted.update(_ - lang)),
          bound.signal --> (value => observed.update(_ :+ (lang -> value)))
        ))
      }
    }}
    val editor = EvaEditor(logger, state, Nil, EvaEditorConfig(enabledLanguages = languages), Some(choices))
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
}
