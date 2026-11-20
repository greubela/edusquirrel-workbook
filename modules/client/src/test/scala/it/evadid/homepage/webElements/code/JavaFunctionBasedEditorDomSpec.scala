package it.evadid.homepage.webElements.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import org.scalajs.dom
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

/** The method-at-a-time Java editor driven through its class list, view toggles, dialogs and text field. */
class JavaFunctionBasedEditorDomSpec extends UiDomSuite {
  private given ExecutionContext = ExecutionContext.global
  override protected def jsdomGlobals: List[String] = super.jsdomGlobals :+ "HTMLDialogElement"
  private val logger = Logger.withNameAndPrefixes(Some("java-editor-test"), PrintToStdLogger.printNothing)
  private val source = "public class Main {\n  public static void main(String[] args) {\n    forward(10);\n  }\n\n" +
    "  static int twice(int x) {\n    return x * 2;\n  }\n}\n"
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

  private def code(state: Var[ProgrammingState]): String = state.now() match {
    case ProgrammingStateRaw(Java, value) => value
    case other => fail(s"not Java: $other")
  }
  private def field(root: dom.Element) = all(root, "textarea.code-mirror-editor__fallback").head
  private def heading(root: dom.Element) = one(root, ".java-function-editor__workspace-heading h3").textContent
  private def viewStatus(root: dom.Element) = one(root, ".java-function-editor__sync").textContent
  private def functionButton(root: dom.Element, name: String) = withText(root, ".java-function-editor__function", s"$name()").head
  private def settle: Future[Unit] = eventually(false, attempts = 2).recover(_ => ())
  private def editor(state: Var[ProgrammingState], reference: Option[() => Element] = None) =
    new JavaFunctionBasedEditor(logger, state, reference = reference)

  test("the public main method opens first; method edits are merged into the full source") {
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, source))
    val javaEditor = editor(state)
    assert(!javaEditor.dismissOnOutsideClick)
    mountedAsync(javaEditor.getDomElement()) { root =>
      assertEquals(viewStatus(root), "Method view")
      assertEquals(heading(root), "Main.main")
      assert(valueOf(field(root)).startsWith("public static void main(String[] args)"), valueOf(field(root)))
      assert(!valueOf(field(root)).contains("twice"))
      assertEquals(functionButton(root, "main").getAttribute("aria-pressed"), "true")
      assertEquals(all(root, ".java-function-editor__class-name").map(_.textContent), List("Main"))
      typeInto(field(root), "public static void main(String[] args) {\n    forward(20);\n    left(90);\n  }")
      eventually(code(state).contains("left(90)"), what = "merged method").flatMap { _ =>
        assertEquals(code(state), source.replace("forward(10);\n", "forward(20);\n    left(90);\n"))
        assertEquals(heading(root), "Main.main")
        typeInto(field(root), "public static void main(String[] args) {\n    forward(")
        eventually(code(state).contains("forward(\n"), what = "unfinished draft kept").map { _ =>
          assert(!one(root, ".java-function-editor__draft-warning").classList.contains("java-function-editor__draft-warning--hidden"),
            "an unbalanced draft is flagged")
          assert(all(root, ".java-function-editor__function").forall(isDisabled), "navigation waits for balanced source")
          assert(isDisabled(buttonNamed(one(root, ".java-function-editor__section-heading"), "Add class")))
          assert(code(state).contains("static int twice"), "the rest of the source is untouched")
        }
      }
    }
  }

  test("selecting methods from the list or the compact menu, and switching between method and full source") {
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, source))
    mountedAsync(editor(state, Some(() => p("Draw a square"))).getDomElement()) { root =>
      assertEquals(one(root, "aside[aria-label='Task reference']").textContent, "Draw a square")
      click(functionButton(root, "twice"))
      assertEquals(heading(root), "Main.twice")
      assert(valueOf(field(root)).startsWith("static int twice(int x)"))
      val menu = one(root, "select[aria-label='Java method']")
      val mainKey = all(menu, "option").find(_.textContent.startsWith("Main.main")).get.getAttribute("value")
      choose(menu, mainKey)
      assertEquals(heading(root), "Main.main")
      click(buttonNamed(root, "Full source"))
      assertEquals(viewStatus(root), "Full source")
      assertEquals(heading(root), "Java source")
      assertEquals(valueOf(field(root)), source)
      click(one(root, "button[aria-label='Method']"))
      assertEquals(viewStatus(root), "Method view")
      assertEquals(heading(root), "Main.main")
      val toggle = buttonNamed(root, "Hide classes")
      click(toggle)
      assertEquals(toggle.getAttribute("aria-expanded"), "false")
      assertEquals(toggle.textContent, "Show classes")
      assert(root.classList.contains("java-function-based-editor--structure-collapsed"))
      click(buttonNamed(root, "Full source"))
      typeInto(field(root), source.replace("x * 2", "x + x"))
      settle.map(_ => assert(code(state).contains("x + x"), "the full source edits the state directly"))
    }
  }

  test("adding a class and a function through the dialogs validates names and opens the new method") {
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, source))
    mountedAsync(editor(state).getDomElement()) { root =>
      def dialog = one(root, "dialog")
      def dialogField(caption: String) = withText(dialog, "label", caption).map(one(_, "input")).head
      def submit(label: String) = click(buttonNamed(dialog, label))
      def error = one(dialog, "[role=alert]").textContent
      click(buttonNamed(one(root, ".java-function-editor__section-heading"), "Add class"))
      assert(dialog.hasAttribute("open"))
      typeInto(dialogField("Class name"), "1Bad")
      submit("Add class")
      for {
        _ <- eventually(error == "Enter a valid Java class name.")
        _ = typeInto(dialogField("Class name"), "Helper")
        _ = submit("Add class")
        _ <- eventually(all(root, "dialog").isEmpty, what = "class dialog closed")
        _ = assert(code(state).startsWith(source) && code(state).endsWith("\n\nclass Helper {\n}\n"), code(state))
        _ = assertEquals(viewStatus(root), "Full source", "a new class is shown in the full source")
        helperCard = withText(root, ".java-function-editor__class-card", "Helper").head
        _ = assert(helperCard.textContent.contains("No functions yet"))
        _ = click(buttonNamed(helperCard, "Add function"))
        _ = typeInto(dialogField("Function name"), "")
        _ = submit("Add function")
        _ <- eventually(error == "Enter a valid Java function name.")
        _ = typeInto(dialogField("Function name"), "greet")
        _ = typeInto(dialogField("Return type"), " ")
        _ = submit("Add function")
        _ <- eventually(error == "Enter a return type.")
        _ = typeInto(dialogField("Return type"), "String")
        _ = typeInto(dialogField("Parameters"), "String")
        _ = submit("Add function")
        _ <- eventually(error == "The function signature or current Java source is invalid.")
        _ = typeInto(dialogField("Parameters"), "String name")
        _ = click(withText(dialog, "label", "Static method").map(one(_, "input")).head)
        _ = submit("Add function")
        _ <- eventually(all(root, "dialog").isEmpty, what = "function dialog closed")
      } yield {
        assert(code(state).contains("class Helper {\n\n  public String greet(String name) {"), code(state))
        assertEquals(heading(root), "Helper.greet")
        assertEquals(viewStatus(root), "Method view")
      }
    }
  }

  test("dialogs close on cancel; a state that is not Java is reported and locks the structure tools") {
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, source))
    mounted(editor(state).getDomElement()) { root =>
      click(buttonNamed(one(root, ".java-function-editor__section-heading"), "Add class"))
      click(buttonNamed(one(root, "dialog"), "Cancel"))
      assertEquals(all(root, "dialog").size, 0)
      click(buttonNamed(one(root, ".java-function-editor__class-card"), "Add function"))
      dispatch(one(root, "dialog"), "cancel")
      assertEquals(all(root, "dialog").size, 0, "Escape closes the dialog")
      assertEquals(code(state), source)

      state.set(ProgrammingStateRaw(Python, "forward(10)\n"))
      assert(one(root, "[role=alert]").textContent.contains("cannot be shown as Java"))
      assert(isDisabled(buttonNamed(root, "Full source")) && isDisabled(one(root, "button[aria-label='Method']")))
      assert(root.textContent.contains("No classes yet"))
      state.set(ProgrammingStateRaw(Java, source.replace("forward(10)", "forward(70)")))
      assertEquals(all(root, "[role=alert]").filter(_.textContent.contains("cannot be shown")).size, 0)
      assertEquals(viewStatus(root), "Full source", "the selection was lost while the state was not Java")
      click(functionButton(root, "main"))
      assert(valueOf(field(root)).contains("forward(70)"))
      state.set(ProgrammingStateRaw(Java, source.replace("forward(10)", "forward(80)")))
      assert(valueOf(field(root)).contains("forward(80)"), "outside changes refresh the open method")
    }
  }

  test("a program without a public main starts in the full source; the screen-size listener is released on unmount") {
    val window = EditorDomSuite.window
    val listeners = Var(0)
    val query = js.Dynamic.literal(matches = false,
      addEventListener = ((_: String, _: js.Any) => listeners.update(_ + 1)): js.Function2[String, js.Any, Unit],
      removeEventListener = ((_: String, _: js.Any) => listeners.update(_ - 1)): js.Function2[String, js.Any, Unit])
    window.updateDynamic("matchMedia")(((_: String) => query): js.Function1[String, js.Dynamic])
    try {
      val state = Var[ProgrammingState](ProgrammingStateRaw(Java, "class Main {\n  static void main(String[] args) {}\n  void draw() {}\n}\n"))
      mounted(editor(state).getDomElement()) { root =>
        assert(listeners.now() > 0, "the editor follows the screen size")
        assertEquals(viewStatus(root), "Full source")
        click(functionButton(root, "draw"))
        assertEquals(heading(root), "Main.draw")
      }
      assertEquals(listeners.now(), 0, "unmounting stops listening to the screen size")
    } finally js.special.delete(window, "matchMedia")
  }
}
