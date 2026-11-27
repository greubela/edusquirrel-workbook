package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class EvaEditorSpec extends EditorDomSuite {
  private val specLabels = EvaEditorLabels.fromContent(_ => Val("_"))

  private class Fixture(source: ProgrammingState, languages: List[ProgrammingLanguage] = List(SnapLanguage, Python, Java)) {
    val state = Var(source)
    val created = Var(Vector.empty[ProgrammingLanguage])
    val mounted = Var(Set.empty[ProgrammingLanguage])
    val observed = Var(Vector.empty[(ProgrammingLanguage, ProgrammingState)])
    val maximum = Var(0)
    val choices = languages.distinct.map { lang => new EvaEditorTab {
      override val language = lang
      override def create(bound: Var[ProgrammingState], context: EvaEditorContext): HtmlAppElement = {
        created.update(_ :+ lang)
        HtmlAppElement(div(
          dataAttr("spec-editor") := lang.name,
          // Var updates inside the replacing transaction are deferred; the document shows what is mounted.
          onMountCallback { _ =>
            mounted.update(_ + lang)
            maximum.update(_ max dom.document.querySelectorAll("[data-spec-editor]").length)
          },
          onUnmountCallback(_ => mounted.update(_ - lang)),
          bound.signal --> (value => observed.update(_ :+ (lang -> value)))
        ))
      }
    }}
    val editor = EvaEditor(state, Var(EvaEditorConfig(enabledLanguages = languages)), EvaTestContext.environment(specLabels), Some(choices))
    val close = mount(editor.getDomElement())
  }

  test("startup creates only the matching editor; changes update it without creating another") {
    val source = ProgrammingStateRaw(Java, "class Unfinished {")
    val f = new Fixture(source)
    try {
      assertEquals(f.created.now(), Vector(Java))
      assertEquals(f.mounted.now(), Set[ProgrammingLanguage](Java))
      val restored = ProgrammingStateRaw(Java, "class Restored {\n")
      f.state.set(restored)
      assertEquals(f.created.now(), Vector(Java))
      assertEquals(f.observed.now().last, Java -> restored)
    } finally f.close()
    assertEquals(f.mounted.now(), Set.empty)
  }
  test("startup follows each state representation independently of tab order") {
    val sources = Seq[ProgrammingState](
      ProgrammingStateRaw(Python, "unfinished("),
      ProgrammingStatePythonStructured("forward(1)"),
      ProgrammingStateRaw(Java, "class Unfinished {"),
      ProgrammingStateJavaStructured("class Drawing {}"),
      ProgrammingStateSnapScalaModel.mini,
      ProgrammingStateRaw(Python, "forward(1)").toBeExpression)
    sources.foreach { source =>
      val f = new Fixture(source, List(Java, BlockDisplay, Python, SnapLanguage))
      try {
        assertEquals(f.editor.selectedLanguage, Some(source.associatedLanguage))
        assertEquals(f.created.now(), Vector(source.associatedLanguage))
        assertEquals(f.state.now(), source)
      } finally f.close()
    }
  }

  test("a missing native tab leaves the answer intact until an external entity selects one") {
    val source = ProgrammingStatePythonStructured("forward(1)")
    val f = new Fixture(source, List(Java))
    assertEquals(f.state.now(), source)
    assertEquals(f.created.now(), Vector.empty)
    assertEquals(f.editor.selectedLanguage, None)
    f.editor.setActiveTab(Java).map { _ =>
      assertEquals(f.editor.selectedLanguage, Some(Java))
      assertEquals(f.state.now(), source.toJavaStructured)
      assertEquals(f.created.now(), Vector(Java))
    }.andThen { case _ => f.close() }
  }

  test("switching converts the central answer and replaces the editor; returning creates a fresh one") {
    val source = ProgrammingStateRaw(Python, "forward(12)")
    val f = new Fixture(source)
    f.editor.setActiveTab(Java).flatMap { _ =>
      assertEquals(f.state.now(), source.toJavaStructured)
      assertEquals(f.created.now(), Vector(Python, Java))
      assertEquals(f.mounted.now(), Set[ProgrammingLanguage](Java))
      f.editor.setActiveTab(Python)
    }.map { _ =>
      assertEquals(f.created.now(), Vector(Python, Java, Python))
      assertEquals(f.mounted.now(), Set[ProgrammingLanguage](Python))
      assert(f.maximum.now() <= 1)
    }.andThen { case _ => f.close() }
  }
  test("switching tabs keeps a program built from reporters: Snap blocks, Java methods and back to Python") {
    val source = ProgrammingStateRaw(Python, "def double(x: float) -> float:\n    return x * 2\n\ndef big(n: float) -> bool:\n    return n > 5\n\nif big(double(3)):\n    forward(double(5))\n")
    def drawing(state: ProgrammingState) = state.toBeExpression.turtleCommandExecutionInfo.completedCommands
    val expected = drawing(source)
    assertEquals(expected.flatMap(_.args), List(10.0))
    val f = new Fixture(source)
    f.editor.setActiveTab(SnapLanguage).flatMap { _ =>
      assertEquals(f.state.now().associatedLanguage, SnapLanguage)
      assertEquals(drawing(f.state.now()), expected)
      val types = f.state.now().toSnapXml.toSnapScalaModel.toOption.get.model.root.descendants.collect {
        case definition: it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel.BlockDefinition => definition.blockType.map(_.xml)
      }
      assertEquals(types.toSet, Set[Option[String]](Some("reporter"), Some("predicate")))
      f.editor.setActiveTab(Java)
    }.flatMap { _ =>
      assert(f.state.now().toString.contains("static double double_(double x)"), f.state.now().toString)
      assert(f.state.now().toString.contains("static boolean big(double n)"), f.state.now().toString)
      assertEquals(drawing(f.state.now()), expected)
      f.editor.setActiveTab(Python)
    }.map { _ =>
      assert(f.state.now().toString.contains("def double(x: float) -> float:"), f.state.now().toString)
      assertEquals(drawing(f.state.now()), expected)
      assertEquals(f.editor.error, None)
    }.andThen { case _ => f.close() }
  }

  test("all central changes are observed without an editor-specific edit channel") {
    val f = new Fixture(ProgrammingStateRaw(Python, "forward(1)"))
    try {
      f.state.set(ProgrammingStateRaw(Python, "forward(2)"))
      f.state.set(ProgrammingStateRaw(Python, "forward(3)"))
      assertEquals(f.observed.now().map(_._2), Vector(ProgrammingStateRaw(Python, "forward(1)"),
        ProgrammingStateRaw(Python, "forward(2)"), ProgrammingStateRaw(Python, "forward(3)")))
    } finally f.close()
  }
  test("failed conversion retains the draft and mounted editor") {
    val source = ProgrammingStateRaw(Java, "class Unfinished {")
    val f = new Fixture(source)
    f.editor.setActiveTab(Python).map { _ =>
      assertEquals(f.state.now(), source)
      assertEquals(f.created.now(), Vector(Java))
      assertEquals(f.mounted.now(), Set[ProgrammingLanguage](Java))
      assert(f.editor.error.nonEmpty)
    }.andThen { case _ => f.close() }
  }
  test("external language restores replace the active editor and release its subscription") {
    val f = new Fixture(ProgrammingStateRaw(Python, "forward(1)"))
    try {
      f.state.set(ProgrammingStateRaw(Java, "unfinished class"))
      val count = f.observed.now().count(_._1 == Python)
      f.state.set(ProgrammingStateRaw(Java, "restored class"))
      assertEquals(f.created.now(), Vector(Python, Java))
      assertEquals(f.observed.now().count(_._1 == Python), count)
      assertEquals(f.mounted.now(), Set[ProgrammingLanguage](Java))
    } finally f.close()
  }
  test("the latest requested conversion wins") {
    val f = new Fixture(ProgrammingStateRaw(Python, "forward(1)"))
    val first = f.editor.setActiveTab(Java)
    val last = f.editor.setActiveTab(SnapLanguage)
    Future.sequence(List(first, last)).map { _ =>
      assertEquals(f.editor.selectedLanguage, Some(SnapLanguage))
      assertEquals(f.created.now(), Vector(Python, SnapLanguage))
    }.andThen { case _ => f.close() }
  }
  test("state changes and unmount cancel pending conversions, including A-to-B-to-A") {
    val original = ProgrammingStateRaw(Python, "forward(1)")
    val f = new Fixture(original)
    val pending = f.editor.setActiveTab(Java)
    f.state.set(ProgrammingStateRaw(Python, "forward(2)"))
    f.state.set(original)
    pending.flatMap { _ =>
      assertEquals(f.created.now(), Vector(Python))
      val closed = f.editor.setActiveTab(Java)
      f.close()
      closed
    }.map(_ => assertEquals(f.state.now(), original))
  }
  test("empty configurations do not create any editor") {
    val f = new Fixture(ProgrammingStateRaw(Python, "unfinished("), Nil)
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
    val source = ProgrammingStateRaw(Java, "class Unfinished {")
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
    val source = ProgrammingStateRaw(Python, "forward(1)")
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
    val f = new Fixture(ProgrammingStateRaw(Python, "forward(1)"))
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
