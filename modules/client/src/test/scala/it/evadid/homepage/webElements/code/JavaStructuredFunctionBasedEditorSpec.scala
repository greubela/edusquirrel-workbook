package it.evadid.homepage.webElements.code

import com.raquo.laminar.api.L.*
import com.raquo.airstream.core.AirstreamError
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorLabels}
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class JavaStructuredFunctionBasedEditorSpec extends EditorDomSuite {
  private val logger = Logger.withNameAndPrefixes(Some("structured-editor-test"), PrintToStdLogger.printNothing)
  private val code = "class Helper { void other() {} }\n\nclass Main { static void helper() {} static void main(String[] args) { forward(10); } }"
  private def field = dom.document.querySelector("textarea").asInstanceOf[dom.html.TextArea]
  private def edited(): Unit = field.dispatchEvent(js.Dynamic.newInstance(
    dom.window.asInstanceOf[js.Dynamic].Event)("input", js.Dynamic.literal(bubbles = true)).asInstanceOf[dom.Event])
  private def withEditor(state: Var[ProgrammingStateJavaStructured])(body: => Future[Unit]): Future[Unit] = {
    val close = mount(new JavaStructuredFunctionBasedEditor(logger, state).getDomElement())
    Future.unit.flatMap(_ => body).andThen { case _ => close() }
  }

  test("structured editor opens non-public Main.main and writes edits to the class map") {
    val state = Var(ProgrammingStateJavaStructured(code))
    withEditor(state) {
      assert(field.value.contains("static void main("), field.value)
      assert(!field.value.contains("class Main"))
      val helper = state.now().definedClasses(BeEntityName.fromCodeString("Helper"))
      field.value = "static void main(String[] args) { forward(25); }"
      edited()
      Future(()).map { _ =>
        assert(state.now().toString.contains("forward(25)"), state.now().toString)
        assertEquals(state.now().definedClasses(BeEntityName.fromCodeString("Helper")), helper)
      }
    }
  }

  test("external structured changes update the selected method and unfinished edits survive") {
    val state = Var(ProgrammingStateJavaStructured(code))
    withEditor(state) {
      state.set(ProgrammingStateJavaStructured(code.replace("forward(10)", "forward(30)")))
      assert(field.value.contains("forward(30)"), field.value)
      field.value = "static void main(String[] args) { forward("
      edited()
      Future(()).map { _ =>
        assert(state.now().toString.contains("forward("), state.now().toString)
        assert(!state.now().toString.contains("forward(30)"), state.now().toString)
      }
    }
  }

  test("the default Java tab projects raw transport and publishes only structured method edits") {
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, code))
    val editor = EvaEditor(logger, state, Nil, Var(EvaEditorConfig(enabledLanguages = Seq(Java))),
      labels = EvaEditorLabels(Val("Unavailable"), Val("Languages"), Val("Failed"), Val("Loading"), it.evadid.homepage.webElements.editor.blocks.BlockEditorLabels.fromContent(_ => Val("_"))))
    val close = mount(editor.getDomElement())
    Future.unit.flatMap { _ =>
      assert(field.value.contains("forward(10)"), field.value)
      field.value = "static void main(String[] args) { forward(45); }"
      edited()
      Future.unit.map { _ =>
        assert(state.now().isInstanceOf[ProgrammingStateJavaStructured])
        assert(state.now().toString.contains("forward(45)"))
      }
    }.andThen { case _ => close() }
  }

  test("structured selection prefers Main and leaves the string editor's public-main rule intact") {
    assertEquals(JavaFunctionBasedEditor.initialFunction(code), None)
    assertEquals(JavaFunctionBasedEditor.initialFunction(code, requirePublicMain = false).map(_.name), Some("main"))
    val withOtherMain = "class Other { public static void main(String[] args) {} }\n" + code
    assertEquals(JavaFunctionBasedEditor.initialFunction(withOtherMain, requirePublicMain = false).map(_.className), Some("Main"))
  }

  test("Eva switches between the structured Java tab and the unchanged Python string editor") {
    val errors = Var(Vector.empty[Throwable])
    val collectErrors: Throwable => Unit = error => errors.update(_ :+ error)
    AirstreamError.registerUnhandledErrorCallback(collectErrors)
    val state = Var[ProgrammingState](ProgrammingStateJavaStructured(code))
    val config = CodeMirrorEditorConfig.Default
    val editor = EvaEditor(logger, state, Nil, Var(EvaEditorConfig(enabledLanguages = List(Java, Python))),
      Some(List(JavaStructuredFunctionBasedEditor.forEvaTab(logger, config), CodeMirrorEditor.pythonEditorForEva(logger, config))),
      labels = EvaEditorLabels(Val("Unavailable"), Val("Languages"), Val("Failed"), Val("Loading"), it.evadid.homepage.webElements.editor.blocks.BlockEditorLabels.fromContent(_ => Val("_"))))
    val close = mount(editor.getDomElement())
    editor.setActiveTab(Python).flatMap { _ =>
      assert(state.now().isInstanceOf[ProgrammingStatePythonStructured])
      assert(field.value.contains("forward(10)"), field.value)
      field.value = field.value.replace("forward(10)", "forward(35)")
      edited()
      editor.setActiveTab(Java)
    }.map { _ =>
      assert(state.now().isInstanceOf[ProgrammingStateJavaStructured])
      assert(field.value.contains("forward(35)"), field.value)
      assert(!field.value.contains("class Main"))
      assertEquals(state.now().toBeExpression.turtleCommandExecutionInfo.completedCommands.flatMap(_.args), List(35.0))
      assertEquals(errors.now(), Vector.empty)
    }.andThen { case _ =>
      close()
      AirstreamError.unregisterUnhandledErrorCallback(collectErrors)
    }
  }
}
