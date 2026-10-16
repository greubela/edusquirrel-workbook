package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.*
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.elements.interactionElements.programming.state.*
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class EvaEditorSpec extends EditorDomSuite {
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
