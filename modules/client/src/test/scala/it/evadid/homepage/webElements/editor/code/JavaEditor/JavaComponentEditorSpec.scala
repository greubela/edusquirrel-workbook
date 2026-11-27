package it.evadid.homepage.webElements.editor.code.JavaEditor

import com.raquo.airstream.core.AirstreamError
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.homepage.webElements.editor.code.{EditorDomSuite, EvaTestContext}
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorLabels}
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.worker.LocalWorker
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** The UML diagram selects what the class or function editor below it shows. */
class JavaComponentEditorSpec extends UiDomSuite {
  override protected def jsdomGlobals: List[String] = super.jsdomGlobals :+ "HTMLDialogElement"
  private val logger = Logger.withNameAndPrefixes(Some("java-component-test"), PrintToStdLogger.printNothing)
  private val mainClass = "public class Main {\n  public static void main(String[] args) {\n    forward(10);\n  }\n\n" +
    "  static int twice(int x) {\n    return x * 2;\n  }\n}"
  private val helperClass = "class Helper {\n  int size = 3;\n  void other() {}\n}"
  private val main = BeEntityName.fromCodeString("Main")
  private val dialogs = EditorDomSuite.window.HTMLDialogElement.prototype
  /** jsdom has no modal dialogs; opening and closing toggle the `open` attribute as browsers do. */
  override def beforeAll(): Unit = {
    super.beforeAll()
    dialogs.updateDynamic("showModal")(((self: js.Dynamic) => self.setAttribute("open", "")): js.ThisFunction0[js.Dynamic, Unit])
    dialogs.updateDynamic("close")(((self: js.Dynamic) => self.removeAttribute("open")): js.ThisFunction0[js.Dynamic, Unit])
  }
  override def afterAll(): Unit = {
    js.special.delete(dialogs, "showModal"); js.special.delete(dialogs, "close")
    super.afterAll()
  }

  /** The English `blockeditor` map, so the assertions read the texts users see. */
  private lazy val english: Map[String, String] = upickle.default.read[Map[String, String]](
    js.Dynamic.global.selectDynamic("require")("fs").readFileSync("resources/languageMaps/eva/blockeditor/map-en.json", "utf8")
      .asInstanceOf[String]).map((key, value) => key.toLowerCase -> value)
  private lazy val labels = JavaEditorLabels.fromContent(id => Val(english.getOrElse(id.entryKey, id.fullId)))

  private def answer(source: String = mainClass + "\n\n" + helperClass) = Var(ProgrammingStateJavaStructured(source))
  private def editor(state: Var[ProgrammingStateJavaStructured], reference: Option[() => Element] = None) =
    new JavaComponentEditor(logger, state, labels, Val("Loading"), reference = reference)
  /** The editor below the toolbar controls Eva shows above it. */
  private def view(component: JavaComponentEditor) = div(component.toolbarControls, component.getDomElement())
  private def field(root: dom.Element) = one(root, "textarea.code-mirror-editor__fallback")
  private def heading(root: dom.Element) = one(root, ".java-function-editor__workspace-heading h3").textContent
  private def status(root: dom.Element) = one(root, ".java-function-editor__sync").textContent
  private def entry(root: dom.Element, label: String) = withText(root, ".uml-entry", label).find(_.textContent == label)
    .getOrElse(fail(s"No diagram entry '$label'"))
  private def classCode(state: Var[ProgrammingStateJavaStructured], name: String) =
    state.now().definedClasses(BeEntityName.fromCodeString(name))


  test("the entry point opens in the function editor; its edits change only that method") {
    val state = answer()
    val component = editor(state)
    assert(!component.dismissOnOutsideClick)
    mountedAsync(view(component)) { root =>
      assertEquals(status(root), "Function view")
      assertEquals(heading(root), "Main.main")
      assert(valueOf(field(root)).startsWith("public static void main(String[] args)"), valueOf(field(root)))
      assertEquals(entry(root, "+main(args: String[]): void").getAttribute("aria-pressed"), "true")
      assertEquals(all(root, ".uml-class").size, 2)
      typeInto(field(root), "public static void main(String[] args) {\n    forward(20);\n  }")
      eventually(classCode(state, "Main").contains("forward(20)"), "method written").flatMap { _ =>
        assertEquals(classCode(state, "Main"), mainClass.replace("forward(10)", "forward(20)"))
        assertEquals(classCode(state, "Helper"), helperClass)
        typeInto(field(root), "public static void main(String[] args) {\n    forward(")
        eventually(classCode(state, "Main").contains("forward(\n"), "unfinished draft kept")
      }.map { _ =>
        assert(!one(root, ".java-function-editor__draft-warning").classList.contains("java-function-editor__draft-warning--hidden"))
        assert(isDisabled(one(root, "fieldset.java-component-editor__diagram")), "the diagram waits for balanced source")
        assert(isDisabled(buttonNamed(root, "Add class")))
        assert(classCode(state, "Main").contains("static int twice"))
      }
    }
  }

  test("classes and attributes open the class editor, functions the function editor, and Full source everything") {
    val state = answer()
    mountedAsync(view(editor(state, Some(() => p("Draw a square"))))) { root =>
      assertEquals(one(root, "aside[aria-label='Task reference']").textContent, "Draw a square")
      click(entry(root, "Helper"))
      assertEquals(status(root), "Class view")
      assertEquals(heading(root), "class Helper")
      assertEquals(valueOf(field(root)), helperClass)
      click(entry(root, "~twice(x: int): int"))
      assertEquals(status(root), "Function view")
      assertEquals(heading(root), "Main.twice")
      assert(valueOf(field(root)).startsWith("static int twice(int x)"))
      click(entry(root, "~size: int"))
      assertEquals(heading(root), "class Helper", "an attribute is edited in its class")
      click(buttonNamed(root, "Full Mode"))
      assertEquals(status(root), "Full source")
      assertEquals(valueOf(field(root)), state.now().toString)
      click(buttonNamed(root, "Class Mode"))
      assertEquals(status(root), "Class view")
      click(buttonNamed(root, "Full Mode"))
      click(entry(root, "Main"))
      assertEquals(status(root), "Class view", "choosing in the diagram leaves the full source")
      typeInto(field(root), mainClass.replace("x * 2", "x + x"))
      eventually(classCode(state, "Main").contains("x + x"), "class edit written").map { _ =>
        val toggle = buttonNamed(root, "Hide classes")
        click(toggle)
        assertEquals(toggle.getAttribute("aria-pressed"), "true")
        assertEquals(buttonNamed(root, "Show classes").getAttribute("aria-pressed"), "false")
        assert(one(root, ".java-component-editor").classList.contains("java-function-based-editor--structure-collapsed"))
      }
    }
  }

  test("adding a class and a function validates names and opens the new entry") {
    val state = answer()
    mountedAsync(view(editor(state))) { root =>
      def dialog = one(root, "dialog")
      def dialogField(caption: String) = withText(dialog, "label", caption).map(one(_, "input")).head
      def submit(label: String) = click(buttonNamed(dialog, label))
      def error = one(dialog, "[role=alert]").textContent
      click(buttonNamed(root, "Add class"))
      typeInto(dialogField("Class name"), "1Bad")
      submit("Add class")
      assertEquals(error, "Enter a valid Java class name.")
      typeInto(dialogField("Class name"), "Helper")
      submit("Add class")
      assertEquals(error, "A class with this name already exists.")
      typeInto(dialogField("Class name"), "Tool")
      submit("Add class")
      assertEquals(all(root, "dialog").size, 0)
      assertEquals(classCode(state, "Tool"), "class Tool {\n}")
      assertEquals(heading(root), "class Tool")
      click(buttonNamed(all(root, ".uml-class").find(_.textContent.contains("Tool")).get, "Add function"))
      typeInto(dialogField("Function name"), "")
      submit("Add function")
      assertEquals(error, "Enter a valid Java function name.")
      typeInto(dialogField("Function name"), "greet")
      typeInto(dialogField("Return type"), "String")
      typeInto(dialogField("Parameters"), "String")
      submit("Add function")
      assertEquals(error, "The function signature or current Java source is invalid.")
      typeInto(dialogField("Parameters"), "String name")
      click(withText(dialog, "label", "Static method").map(one(_, "input")).head)
      submit("Add function")
      assertEquals(all(root, "dialog").size, 0)
      assert(classCode(state, "Tool").contains("public String greet(String name) {"), classCode(state, "Tool"))
      assertEquals(heading(root), "Tool.greet")
      assertEquals(entry(root, "+greet(name: String): String").getAttribute("aria-pressed"), "true")
      click(buttonNamed(root, "Add class"))
      dispatch(one(root, "dialog"), "cancel")
      assertEquals(all(root, "dialog").size, 0, "Escape closes the dialog")
      Future.unit
    }
  }

  test("a second method typed in the function editor opens its class; an added attribute the full source") {
    val state = answer()
    mountedAsync(view(editor(state))) { root =>
      val method = valueOf(field(root))
      typeInto(field(root), method + "\n\n  static void extra() {\n    forward(5);\n  }")
      eventually(status(root) == "Class view", "Class Mode shows both methods").flatMap { _ =>
        assertEquals(heading(root), "class Main")
        assert(valueOf(field(root)).contains("forward(10);") && valueOf(field(root)).contains("forward(5);"), valueOf(field(root)))
        assertEquals(buttonNamed(root, "Class Mode").getAttribute("aria-selected"), "true")
        click(entry(root, "~twice(x: int): int"))
        typeInto(field(root), valueOf(field(root)) + "\n\n  static int size = 3;")
        eventually(status(root) == "Full source", "Full Mode shows the method and the attribute")
      }.map { _ =>
        assertEquals(valueOf(field(root)), state.now().toString)
        assert(classCode(state, "Main").contains("static int size = 3;"))
      }
    }
  }

  test("attributes and constructors are added to the chosen class; deletions preserve the other members") {
    val state = answer()
    mountedAsync(view(editor(state))) { root =>
      def box = all(root, ".uml-class").find(_.querySelector(".uml-class__header").textContent == "Helper").get
      def dialog = one(root, "dialog")
      def input(caption: String) = withText(dialog, "label", caption).map(one(_, "input")).head
      click(buttonNamed(box, "New attribute"))
      typeInto(input("Attribute name"), "count")
      typeInto(input("Initial value"), "5")
      click(buttonNamed(dialog, "New attribute"))
      assert(classCode(state, "Helper").contains("private int count = 5;"))
      assertEquals(classCode(state, "Main"), mainClass)
      click(buttonNamed(box, "New constructor"))
      typeInto(input("Parameters"), "int count")
      click(buttonNamed(dialog, "New constructor"))
      assert(classCode(state, "Helper").contains("this.count = count;"))
      assertEquals(heading(root), "Helper.Helper")
      click(buttonNamed(box, "Delete"))
      assert(!classCode(state, "Helper").contains("this.count = count;"))
      assert(classCode(state, "Helper").contains("void other()"))
      click(entry(root, "-count: int"))
      assertEquals(valueOf(input("Initial value")), "5")
      typeInto(input("Initial value"), "7")
      click(buttonNamed(dialog, "Edit attribute"))
      assert(classCode(state, "Helper").contains("private int count = 7;"))
      click(entry(root, "-count: int"))
      click(buttonNamed(dialog, "Delete"))
      assert(!classCode(state, "Helper").contains("int count"))
      assert(classCode(state, "Helper").contains("int size = 3;"))
      Future.unit
    }
  }

  test("outside changes refresh the open method; a removed selection shows the placeholder") {
    val state = answer()
    mounted(view(editor(state))) { root =>
      state.set(ProgrammingStateJavaStructured(mainClass.replace("forward(10)", "forward(80)") + "\n\n" + helperClass))
      assert(valueOf(field(root)).contains("forward(80)"))
      state.set(ProgrammingStateJavaStructured(helperClass))
      assertEquals(status(root), "No selection")
      assert(one(root, ".java-function-editor__editor-placeholder").textContent.contains("Choose a class or function"))
    }
  }

  test("libraries and unknown classes the program uses appear as read-only boxes after the worker's analysis") {
    val state = answer("public class Main {\n  public static void main(String[] args) {\n    System.out.println(1);\n" +
      "    Score.board.show(2);\n  }\n}")
    val component = new JavaComponentEditor(logger, state, labels, Val("Loading"),
      analyzer = LocalWorker.analyzer())
    mountedAsync(view(component)) { root =>
      def box(name: String) = all(root, ".uml-class").find(_.querySelector(".uml-class__header").textContent.endsWith(name))
      eventually(box("ScoreBoard").nonEmpty, "the analysis adds the external classes").map { _ =>
        assertEquals(all(root, ".uml-class").map(_.querySelector(".uml-class__header").textContent),
          List("Main", "«library»System", "«library»PrintStream", "«unknown»Score", "«unknown»ScoreBoard"))
        assert(box("PrintStream").get.classList.contains("uml-class--library"))
        assertEquals(box("ScoreBoard").get.getAttribute("title"), english("umlunknownclasshint"))
        assertEquals(all(box("Score").get, ".uml-entry").map(_.textContent), List("Score", "+board: ScoreBoard"))
        assertEquals(all(box("ScoreBoard").get, "button").size, 0)
        // Only the program's own class has an "Add function" footer.
        assertEquals(withText(root, ".uml-class__actions button", english("javaaddfunction")).size, 1)
      }.flatMap { _ =>
        val owner = com.raquo.airstream.ownership.ManualOwner()
        val warnings = component.warnings.observe(using owner)
        eventually(warnings.now().nonEmpty, "the analysis locates the unknown use").map { _ =>
          assertEquals(warnings.now().map(mark => (mark.line, mark.fromCh, mark.toCh, mark.severity, mark.message)),
            Seq((4, Some(4), Some(20), "warning", english("sourceunknownuse").replace("{name}", "Score.board.show"))))
          owner.killSubscriptions()
        }
      }
    }
  }

  test("the exercise environment decides which libraries exist: an excluded turtle is an unknown class") {
    import it.evadid.vm.conversion.dependencies.{ProgrammingEnvironment, ProgrammingLibrary}
    val state = answer("public class Main {\n  public static void main(String[] args) {\n    Turtle.forward(10);\n  }\n}")
    val component = new JavaComponentEditor(logger, state, labels, Val("Loading"),
      environment = ProgrammingEnvironment.of(ProgrammingLibrary.System), analyzer = LocalWorker.analyzer())
    mountedAsync(view(component)) { root =>
      eventually(all(root, ".uml-class--unknown").nonEmpty, "the analysis marks the turtle unknown").map { _ =>
        assertEquals(all(root, ".uml-class--unknown").map(_.querySelector(".uml-class__header").textContent), List("«unknown»Turtle"))
      }
    }
  }

  test("a program without a single entry point starts in the full source") {
    val state = answer("class Main {\n  void draw() {}\n}")
    mounted(view(editor(state))) { root =>
      assertEquals(status(root), "Full source")
      click(entry(root, "~draw(): void"))
      assertEquals(heading(root), "Main.draw")
    }
    assertEquals(JavaComponentEditor.entryFunction(answer().now()).map(_.method.name), Some("main"))
    val otherMain = answer("class Other { public static void main(String[] args) {} }\n" + mainClass)
    assertEquals(JavaComponentEditor.entryFunction(otherMain.now()).map(_.className), Some(main), "class Main is preferred")
  }

  private lazy val evaLabels = EvaEditorLabels.fromContent(id => Val(english.getOrElse(id.entryKey, id.fullId)))
  private def textarea = dom.document.querySelector("textarea").asInstanceOf[dom.html.TextArea]

  test("the Eva Java tab projects raw transport and publishes structured method edits") {
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, mainClass))
    val eva = EvaEditor(state, Var(EvaEditorConfig(enabledLanguages = Seq(Java))), EvaTestContext.environment(evaLabels))
    val close = mount(eva.getDomElement())
    Future.unit.flatMap { _ =>
      val toolbar = one(dom.document.body, ".eva-editor__toolbar")
      assertEquals(all(toolbar, "[role=tablist]").map(_.getAttribute("aria-label")), List("Programming languages", "Java editing mode"))
      assertEquals(buttonNamed(toolbar, "Show classes").getAttribute("aria-pressed"), "true")
      assertEquals(all(dom.document.body, ".programming-execution__actions button").map(_.textContent), List("Run", "Stop", "Download project"))
      assert(textarea.value.contains("forward(10)"), textarea.value)
      typeInto(textarea, "public static void main(String[] args) { forward(45); }")
      eventually(state.now().toString.contains("forward(45)"), "edit published")
    }.map(_ => assert(state.now().isInstanceOf[ProgrammingStateJavaStructured])).andThen { case _ => close() }
  }

  test("Eva switches between the Java component editor and the Python editor") {
    val errors = Var(Vector.empty[Throwable])
    val collectErrors: Throwable => Unit = error => errors.update(_ :+ error)
    AirstreamError.registerUnhandledErrorCallback(collectErrors)
    val state = Var[ProgrammingState](ProgrammingStateJavaStructured("class Main { static void main(String[] args) { forward(10); } }"))
    val config = CodeMirrorEditorConfig.Default
    val eva = EvaEditor(state, Var(EvaEditorConfig(enabledLanguages = List(Java, Python))), EvaTestContext.environment(evaLabels),
      Some(List(JavaComponentEditor.forEvaTab(config), it.evadid.homepage.webElements.editor.code.PythonEditor.PythonComponentEditor.forEvaTab(config))))
    val close = mount(eva.getDomElement())
    eva.setActiveTab(Python).flatMap { _ =>
      assert(state.now().isInstanceOf[ProgrammingStatePythonStructured])
      typeInto(textarea, textarea.value.replace("forward(10)", "forward(35)"))
      eventually(state.now().toPython.code.contains("forward(35)"), "the Python method edit reached the answer")
        .flatMap(_ => eva.setActiveTab(Java))
    }.map { _ =>
      assert(state.now().isInstanceOf[ProgrammingStateJavaStructured])
      assert(textarea.value.contains("forward(35)"), textarea.value)
      assert(!textarea.value.contains("class Main"), "Java opens the function editor")
      assertEquals(errors.now(), Vector.empty)
    }.andThen { case _ =>
      close()
      AirstreamError.unregisterUnhandledErrorCallback(collectErrors)
    }
  }
}
