package it.evadid.homepage.webElements.editor.code.fragment

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.webElements.editor.code.JavaEditor.{JavaClassEditor, JavaFunctionEditor}
import it.evadid.homepage.webElements.editor.code.PythonEditor.{PythonClassEditor, PythonFunctionEditor}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.interactions.programming.state.{ProgrammingStateJavaStructured, ProgrammingStatePythonStructured}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Java's and Python's class and function editors share one update logic: local edits are spliced into the
  * structured answer.
  */
class FragmentCodeEditorSpec extends EditorDomSuite {
  private val logger = Logger.withNameAndPrefixes(Some("fragment-editor-test"), PrintToStdLogger.printNothing)
  private val helperClass = "class Helper {\n  void other() {}\n}"
  private val mainClass = "class Main {\n  static void helper() {}\n  static void main(String[] args) { forward(10); }\n}"
  private val helper = BeEntityName.fromCodeString("Helper")
  private val main = BeEntityName.fromCodeString("Main")
  private def state() = Var(ProgrammingStateJavaStructured(helperClass + "\n\n" + mainClass))
  private def classes(state: Var[ProgrammingStateJavaStructured]) = state.now().definedClasses

  private def withMounted[E <: FragmentCodeEditor[?, ?]](editor: E)(body: E => Future[Unit]): Future[Unit] = {
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

  test("child cleanup commits the final fragment, but cannot overwrite a newer restored answer") {
    def closing(restored: Boolean): Future[Unit] = {
      val answer = state()
      val editor = JavaFunctionEditor(logger, answer, Val("loading"))
      val finalDraft = "static void main(String[] args) { forward(55); }"
      val close = mount(div(editor.getDomElement(), span(onUnmountCallback(_ => editor.local.set(finalDraft)))))
      editor.select(mainFunction(answer))
      close()
      val replacement = ProgrammingStateJavaStructured("class NewAnswer {}")
      if restored then answer.set(replacement)
      Future.unit.map { _ =>
        if restored then assertEquals(answer.now(), replacement)
        else assertEquals(classes(answer)(main), mainClass.replace("forward(10)", "forward(55)"))
      }
    }
    closing(false).flatMap(_ => closing(true))
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

  /** The scope `editor` reports once `draft` replaced its fragment `identity` in the answer. */
  private def widenedBy[S, I](editor: FragmentCodeEditor[S, I], identity: I, draft: String => String): Future[Option[FragmentCodeEditor.Scope]] = {
    val close = mount(editor.getDomElement())
    val owner = ManualOwner()
    val widened = editor.widened.observe(using owner)
    editor.select(identity)
    val (before, written) = (editor.state.now(), draft(editor.local.now()))
    assertEquals(widened.now(), None)
    editor.local.set(written)
    eventually(editor.state.now() != before, "draft written").map { _ =>
      assertEquals(editor.local.now(), written, "the whole draft stays in the editor")
      widened.now()
    }.andThen { case _ => owner.killSubscriptions(); close() }
  }

  test("any code typed around a Java method widens the draft to its class; code outside a class to the whole program") {
    import FragmentCodeEditor.Scope
    def widened(draft: String => String) = { val answer = state(); widenedBy(JavaFunctionEditor(logger, answer, Val("loading")), mainFunction(answer), draft) }
    val methods = state()
    widenedBy(JavaFunctionEditor(logger, methods, Val("loading")), mainFunction(methods), _ + "\n  static void extra() { forward(42); }")
      .flatMap { scope =>
        assertEquals(scope, Some(Scope.Owner))
        assert(classes(methods)(main).contains("forward(10); }\n  static void extra() { forward(42); }"), classes(methods)(main))
        widened(_ + "\n  static int size = 3;")
      }.flatMap { scope =>
        assertEquals(scope, Some(Scope.Owner), "a method and an attribute are shown by their class")
        widened(_ + "\n  int")
      }.flatMap { scope =>
        assertEquals(scope, Some(Scope.Owner), "unfinished code after the method switches at once")
        val twoClasses = state()
        widenedBy(JavaClassEditor(logger, twoClasses, Val("loading")), helper, _ + "\n\nclass Extra {")
      }.flatMap { scope =>
        assertEquals(scope, None, "unclosed brackets are still being typed")
        val twoClasses = state()
        widenedBy(JavaClassEditor(logger, twoClasses, Val("loading")), helper, _ + "\n\nclass Extra {}")
      }.map(scope => assertEquals(scope, Some(Scope.Program), "code outside the class needs the whole program"))
  }

  test("a statement typed inside the method of an answer view widens nothing and reaches the answer") {
    import it.evadid.workbook.interactions.programming.state.ProgrammingState
    // As in Eva: the editor's answer is a view of the central answer.
    val central = Var[ProgrammingState](ProgrammingStateJavaStructured(helperClass + "\n\n" + mainClass))
    val view = it.evadid.homepage.webElements.editor.code.ProgrammingStateView(central, ProgrammingStateJavaStructured(Map.empty)) {
      case value: ProgrammingStateJavaStructured => value
    }(identity)
    widenedBy(JavaFunctionEditor(logger, view, Val("loading")), mainFunction(view), _.replace("forward(10);", "forward(10);\n    forward(20);"))
      .map { scope =>
        assertEquals(scope, None)
        assert(central.now().toString.contains("forward(20)"), central.now().toString)
      }
  }

  test("cursor offsets map between a dedented draft and its stored form, and to the printed program") {
    val stored = "    def f(self):\n        return 1\n"
    val shown = "def f(self):\n    return 1"
    assertEquals(FragmentCodeEditor.mapOffset(shown, stored, shown.indexOf("return")), stored.indexOf("return"))
    assertEquals(FragmentCodeEditor.mapOffset(stored, shown, stored.indexOf("1")), shown.indexOf("1"))
    assertEquals(FragmentCodeEditor.mapOffset(mainClass, mainClass, 17), 17, "identical texts keep the offset")
    val answer = state()
    val range = FragmentRange(main, mainClass.indexOf("static void main"), mainClass.length - 2)
    val printed = range.printedOffset(answer.now(), 3)
    assertEquals(answer.now().toString.substring(printed, printed + 4), "tic ")
    assertEquals(range.fragmentOffset(answer.now(), printed), Some(3))
    assertEquals(range.fragmentOffset(answer.now(), 0), None, "before the fragment")
  }

  test("fragment ranges map to lines of the printed program") {
    val answer = state()
    val range = FragmentRange(main, mainClass.indexOf("static void main"), mainClass.length - 2)
    assertEquals(range.text(answer.now()).map(_.take(16)), Some("static void main"))
    assertEquals(range.firstLineNumber(answer.now()), 7)
    assertEquals(FragmentRange(helper, 0, 1).firstLineNumber(answer.now()), 1)
  }

  test("diagnostics of the printed program move into the fragment's own lines; others are left out") {
    import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor.Diagnostic
    val answer = state()
    val range = FragmentRange(main, mainClass.indexOf("static void main"), mainClass.length - 2)
    val line = "  static void main(String[] args) { forward(10); }"
    val call = Diagnostic(7, Some(7), Some(line.indexOf("forward")), Some(line.indexOf("(10)")), "unknown", "warning")
    assertEquals(range.localize(answer.now(), call),
      Some(call.copy(line = 1, endLine = Some(1), fromCh = Some(line.indexOf("forward") - 2), toCh = Some(line.indexOf("(10)") - 2))))
    assertEquals(range.localize(answer.now(), Diagnostic(7, None, None, None, "error", "error")),
      Some(Diagnostic(1, Some(1), None, None, "error", "error")), "a whole-line mark stays whole")
    assertEquals(range.localize(answer.now(), call.copy(line = 2, endLine = Some(2))), None, "outside the fragment")
    val whole = FragmentRange(main, 0, mainClass.length)
    assertEquals(whole.localize(answer.now(), call).map(mark => (mark.line, mark.fromCh)), Some((3, Some(line.indexOf("forward")))))
  }

  private val boxClass = "class Box:\n    def f(self):\n        return 1\n\n    def g(self):\n        return 2\n"
  private val box = BeEntityName.fromCodeString("Box")
  private def python() = Var(ProgrammingStatePythonStructured(boxClass + "\nb = Box()\n"))

  test("a Python method is edited without its class indentation and stored indented") {
    val answer = python()
    withMounted(PythonFunctionEditor(logger, answer, Val("loading"))) { editor =>
      editor.select(PythonFunctionEditor.Function(box, "f", 0))
      assertEquals(editor.local.now(), "def f(self):\n    return 1")
      editor.local.set("def f(self):\n    return 3")
      eventually(answer.now().definedClasses(box).contains("    def f(self):\n        return 3\n"), "method edit written").map { _ =>
        assert(answer.now().definedClasses(box).contains("    def g(self):\n        return 2"), "the next method is untouched")
        assert(answer.now().mainScript.contains("b = Box()"))
        assertEquals(editor.local.now(), "def f(self):\n    return 3", "the draft is not replaced by its stored form")
      }
    }
  }

  test("an unfinished Python draft stays in place, and an unmounted editor writes nothing more") {
    val answer = python()
    val editor = PythonFunctionEditor(logger, answer, Val("loading"))
    withMounted(editor) { editor =>
      editor.select(PythonFunctionEditor.Function(box, "g", 0))
      editor.local.set("def g(self):\n    return (")
      eventually(answer.now().definedClasses(box).contains("return ("), "unfinished draft written").map(_ => ())
    }.flatMap { _ =>
      val closed = answer.now()
      editor.local.set("def g(self):\n    return 9")
      Future(()).flatMap(_ => Future(())).map(_ => assertEquals(answer.now(), closed, "a released editor does not write"))
    }
  }

  test("any code typed around a Python method widens the draft to its class; code outside a class to the whole program") {
    import FragmentCodeEditor.Scope
    val f = PythonFunctionEditor.Function(box, "f", 0)
    def widened(draft: String => String) = widenedBy(PythonFunctionEditor(logger, python(), Val("loading")), f, draft)
    val methods = python()
    widenedBy(PythonFunctionEditor(logger, methods, Val("loading")), f, _ + "\n\ndef h(self):\n    return 4").flatMap { scope =>
      assertEquals(scope, Some(Scope.Owner))
      assert(methods.now().definedClasses(box).contains("    def h(self):\n        return 4"), methods.now().definedClasses(box))
      widened(_ + "\n\nsize = 3")
    }.flatMap { scope =>
      assertEquals(scope, Some(Scope.Owner), "a method and an attribute are shown by their class")
      widened("p\n" + _)
    }.flatMap { scope =>
      assertEquals(scope, Some(Scope.Owner), "a single character before the method switches at once")
      widened(_ + "\n    pass")
    }.flatMap { scope =>
      assertEquals(scope, None, "the method's own body stays")
      widenedBy(PythonClassEditor(logger, python(), Val("loading")), box, _ + "\nc")
    }.map(scope => assertEquals(scope, Some(Scope.Program), "code outside the class needs the whole program"))
  }

  test("the Python class editor edits the whole class or module and follows a renamed class") {
    val answer = python()
    withMounted(PythonClassEditor(logger, answer, Val("loading"))) { editor =>
      editor.select(box)
      val stored = answer.now().definedClasses(box)
      assert(stored.startsWith("class Box:") && stored.contains("return 2"), stored)
      assertEquals(editor.local.now(), stored)
      editor.local.set(stored.replace("class Box:", "class Crate:"))
      eventually(answer.now().definedClasses.contains(BeEntityName.fromCodeString("Crate")), "class renamed").map { _ =>
        assertEquals(answer.now().definedClasses.keys.toList, List(BeEntityName.fromCodeString("Crate")))
        assertEquals(editor.target.observe(using ManualOwner()).now(), Some(BeEntityName.fromCodeString("Crate")))
      }
    }
  }
}
