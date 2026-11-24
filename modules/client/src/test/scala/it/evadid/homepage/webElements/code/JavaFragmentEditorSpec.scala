package it.evadid.homepage.webElements.code

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The class and function editors share one update logic: local edits are spliced into the structured answer. */
class JavaFragmentEditorSpec extends EditorDomSuite {
  private val logger = Logger.withNameAndPrefixes(Some("fragment-editor-test"), PrintToStdLogger.printNothing)
  private val helperClass = "class Helper {\n  void other() {}\n}"
  private val mainClass = "class Main {\n  static void helper() {}\n  static void main(String[] args) { forward(10); }\n}"
  private val helper = BeEntityName.fromCodeString("Helper")
  private val main = BeEntityName.fromCodeString("Main")
  private def state() = Var(ProgrammingStateJavaStructured(helperClass + "\n\n" + mainClass))
  private def classes(state: Var[ProgrammingStateJavaStructured]) = state.now().definedClasses

  private def withMounted[E <: JavaFragmentEditor[?]](editor: E)(body: E => Future[Unit]): Future[Unit] = {
    val close = mount(editor.getDomElement())
    Future.unit.flatMap(_ => body(editor)).andThen { case _ => close() }
  }

  private def mainFunction(state: Var[ProgrammingStateJavaStructured]) =
    JavaFunctionEditor.find(state.now(), main, "static void main(String[] args) { forward(10); }").get

  test("a class editor edits the whole class; renaming its declaration renames the entry in place") {
    val answer = state()
    withMounted(JavaClassEditor(logger, answer, Val("loading"))) { editor =>
      editor.select(helper)
      assertEquals(editor.local.now(), helperClass)
      editor.local.set(helperClass.replace("other", "renamed"))
      eventually(classes(answer)(helper).contains("renamed"), "class edit written").flatMap { _ =>
        assertEquals(classes(answer)(main), mainClass, "other classes are untouched")
        editor.local.set(helperClass.replace("class Helper", "class Tool"))
        eventually(!classes(answer).contains(helper), "class renamed")
      }.map { _ =>
        assertEquals(classes(answer).keys.toList.map(_.universalInterpretation()), List("tool", "main"))
        val owner = ManualOwner()
        assertEquals(editor.target.observe(using owner).now().map(_.universalInterpretation()), Some("tool"))
        owner.killSubscriptions()
      }
    }
  }

  test("a function editor writes its method into the class; unfinished code keeps its place") {
    val answer = state()
    withMounted(JavaFunctionEditor(logger, answer, Val("loading"))) { editor =>
      editor.select(mainFunction(answer))
      assertEquals(editor.local.now(), "static void main(String[] args) { forward(10); }")
      editor.local.set("static void main(String[] args) { forward(")
      eventually(classes(answer)(main).contains("forward(\n}"), "unfinished method written").flatMap { _ =>
        assert(classes(answer)(main).startsWith("class Main {\n  static void helper() {}\n"), classes(answer)(main))
        editor.local.set("static void start(String[] args) { forward(25); }")
        eventually(classes(answer)(main).contains("forward(25)"), "finished method written")
      }.map { _ =>
        assertEquals(classes(answer)(main), mainClass.replace("main(String[] args) { forward(10); }", "start(String[] args) { forward(25); }"))
        assertEquals(classes(answer)(helper), helperClass)
        val owner = ManualOwner()
        assertEquals(editor.target.observe(using owner).now().map(_.method.name), Some("start"), "the renamed method stays selected")
        owner.killSubscriptions()
      }
    }
  }

  test("outside changes refresh the local text, follow a moved method and report a removed one") {
    val answer = state()
    withMounted(JavaFunctionEditor(logger, answer, Val("loading"))) { editor =>
      val owner = ManualOwner()
      val located = editor.located.observe(using owner)
      editor.select(mainFunction(answer))
      answer.set(ProgrammingStateJavaStructured(helperClass + "\n\n" +
        "class Main {\n  static void main(String[] args) { forward(30); }\n  static void helper() {}\n}"))
      assertEquals(editor.local.now(), "static void main(String[] args) { forward(30); }")
      assert(located.now())
      answer.set(ProgrammingStateJavaStructured(helperClass))
      assert(!located.now(), "the method is gone")
      assertEquals(editor.local.now(), "static void main(String[] args) { forward(30); }", "the last text stays visible")
      owner.killSubscriptions()
      Future.unit
    }
  }

  test("adding a second declaration selects only the new function for later edits") {
    val answer = state()
    withMounted(JavaFunctionEditor(logger, answer, Val("loading"))) { editor =>
      editor.select(mainFunction(answer))
      val original = editor.local.now()
      val added = "static void extra() { forward(42); }"
      editor.local.set(original + "\n" + added)
      eventually(editor.local.now() == added, "new method selected").flatMap { _ =>
        editor.local.set(added.replace("42", "43"))
        eventually(classes(answer)(main).contains("forward(43)"), "new method edited")
      }.map { _ => assert(classes(answer)(main).contains(original), "the previous method is preserved") }
    }
  }

  test("fragment ranges map to lines of the printed program") {
    val answer = state()
    val range = JavaFragmentRange(main, mainClass.indexOf("static void main"), mainClass.length - 2)
    assertEquals(range.text(answer.now()).map(_.take(16)), Some("static void main"))
    assertEquals(range.firstLineNumber(answer.now()), 7)
    assertEquals(JavaFragmentRange(helper, 0, 1).firstLineNumber(answer.now()), 1)
  }

  test("diagnostics of the printed program move into the fragment's own lines; others are left out") {
    import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor.Diagnostic
    val answer = state()
    val range = JavaFragmentRange(main, mainClass.indexOf("static void main"), mainClass.length - 2)
    val line = "  static void main(String[] args) { forward(10); }"
    val call = Diagnostic(7, Some(7), Some(line.indexOf("forward")), Some(line.indexOf("(10)")), "unknown", "warning")
    assertEquals(range.localize(answer.now(), call),
      Some(call.copy(line = 1, endLine = Some(1), fromCh = Some(line.indexOf("forward") - 2), toCh = Some(line.indexOf("(10)") - 2))))
    assertEquals(range.localize(answer.now(), Diagnostic(7, None, None, None, "error", "error")),
      Some(Diagnostic(1, Some(1), None, None, "error", "error")), "a whole-line mark stays whole")
    assertEquals(range.localize(answer.now(), call.copy(line = 2, endLine = Some(2))), None, "outside the fragment")
    val whole = JavaFragmentRange(main, 0, mainClass.length)
    assertEquals(whole.localize(answer.now(), call).map(mark => (mark.line, mark.fromCh)), Some((3, Some(line.indexOf("forward")))))
  }
}
