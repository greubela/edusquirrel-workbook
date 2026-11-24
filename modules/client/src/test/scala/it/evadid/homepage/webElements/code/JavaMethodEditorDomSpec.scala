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

/** The Java editor driven through its UML class diagram, view toggles, structure dialogs and text field.
  * Labels are the language-map keys, as the editor shows them without a language map.
  */
class JavaMethodEditorDomSpec extends UiDomSuite {
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
  private def umlLine(root: dom.Element) = one(root, ".java-function-editor__workspace-heading .java-uml-line")
  private def viewStatus(root: dom.Element) = one(root, ".java-function-editor__sync").textContent
  private def classBox(root: dom.Element, name: String) = one(root, s".java-uml__class[data-class-name='$name']")
  private def operation(root: dom.Element, name: String) = one(root, s"button.java-uml__operation[data-member='$name']")
  private def addButton(box: dom.Element, label: String) =
    withText(box, ".java-uml__add", label).headOption.getOrElse(fail(s"no add button '$label' in ${box.textContent}"))
  private def settle: Future[Unit] = eventually(false, attempts = 2).recover(_ => ())
  private def editor(state: Var[ProgrammingState], reference: Option[() => Element] = None,
      features: JavaStructureFeatures = JavaStructureFeatures.Full) =
    new JavaMethodEditor(logger, state, reference = reference, features = features)

  private def dialog(root: dom.Element) = one(root, "dialog")
  private def dialogField(root: dom.Element, caption: String) = withText(dialog(root), "label", caption).map(one(_, "input")).head
  private def submit(root: dom.Element) = click(one(dialog(root), "button[data-action='submit']"))
  private def dialogError(root: dom.Element) = one(dialog(root), "[role=alert]").textContent
  private def closed(root: dom.Element) = all(root, "dialog").isEmpty

  test("the public main method opens first; the diagram shows UML notation; method edits are merged into the full source") {
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, source))
    val javaEditor = editor(state)
    assert(!javaEditor.dismissOnOutsideClick)
    mountedAsync(javaEditor.getDomElement()) { root =>
      assertEquals(viewStatus(root), "javaViewDiagram")
      assertEquals(heading(root), "Main.main")
      assertEquals(umlLine(root).textContent, "+ main(args: String[])")
      assert(umlLine(root).classList.contains("java-uml__member--static"), "static operations are underlined")
      assert(valueOf(field(root)).startsWith("public static void main(String[] args)"), valueOf(field(root)))
      assert(!valueOf(field(root)).contains("twice"))
      assertEquals(operation(root, "main").getAttribute("aria-pressed"), "true")
      assertEquals(all(root, ".java-uml__class-name").map(_.textContent), List("Main"))
      assertEquals(all(classBox(root, "Main"), ".java-uml__operation").map(_.textContent), List("+ main(args: String[])", "~ twice(x: int): int"))
      typeInto(field(root), "public static void main(String[] args) {\n    forward(20);\n    left(90);\n  }")
      eventually(code(state).contains("left(90)"), clue = "merged method").flatMap { _ =>
        assertEquals(code(state), source.replace("forward(10);\n", "forward(20);\n    left(90);\n"))
        assertEquals(heading(root), "Main.main")
        typeInto(field(root), "public static void main(String[] args) {\n    forward(")
        eventually(code(state).contains("forward(\n"), clue = "unfinished draft kept").map { _ =>
          assert(!one(root, ".java-function-editor__draft-warning").classList.contains("java-function-editor__draft-warning--hidden"),
            "an unbalanced draft is flagged")
          assert(one(root, ".java-uml").classList.contains("java-uml--draft"), "the diagram shows the last complete version as a draft")
          assertEquals(all(root, ".java-uml__operation").size, 2, "the last complete structure stays visible")
          assert(all(root, ".java-uml__operation").forall(isDisabled), "navigation waits for balanced source")
          assert(isDisabled(addButton(root, "javaAddMethod")))
          assert(code(state).contains("static int twice"), "the rest of the source is untouched")
        }
      }
    }
  }

  test("selecting operations from the diagram or the compact menu, and switching between diagram and full source") {
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, source))
    mountedAsync(editor(state, Some(() => p("Draw a square"))).getDomElement()) { root =>
      assertEquals(one(root, "aside[aria-label='javaReference']").textContent, "Draw a square")
      click(operation(root, "twice"))
      assertEquals(heading(root), "Main.twice")
      assertEquals(umlLine(root).textContent, "~ twice(x: int): int")
      assert(valueOf(field(root)).startsWith("static int twice(int x)"))
      val menu = one(root, "select[aria-label='javaMethodMenu']")
      val mainKey = all(menu, "option").find(_.textContent.startsWith("Main.main")).get.getAttribute("value")
      choose(menu, mainKey)
      assertEquals(heading(root), "Main.main")
      click(one(root, "button[data-view='source']"))
      assertEquals(viewStatus(root), "javaViewSource")
      assertEquals(heading(root), "javaSourceTitle")
      assertEquals(valueOf(field(root)), source)
      assert(all(root, ".java-uml__class").nonEmpty, "the diagram stays beside the full source")
      click(one(root, "button[data-view='diagram']"))
      assertEquals(viewStatus(root), "javaViewDiagram")
      assertEquals(heading(root), "Main.main")
      val toggle = buttonNamed(root, "javaHideDiagram")
      click(toggle)
      assertEquals(toggle.getAttribute("aria-expanded"), "false")
      assertEquals(toggle.textContent, "javaShowDiagram")
      assert(root.classList.contains("java-function-based-editor--structure-collapsed"))
      click(one(root, "button[data-view='source']"))
      typeInto(field(root), source.replace("x * 2", "x + x").replace("static int twice", "static int doubled"))
      settle.map { _ =>
        assert(code(state).contains("x + x"), "the full source edits the state directly")
        assertEquals(all(root, ".java-uml__operation").map(_.getAttribute("data-member")), List("main", "doubled"),
          "the diagram follows typing in the full source")
      }
    }
  }

  test("adding a class and a method through the dialogs validates names and opens the new method") {
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, source))
    mountedAsync(editor(state).getDomElement()) { root =>
      click(addButton(one(root, ".java-uml__heading"), "javaAddClass"))
      assert(dialog(root).hasAttribute("open"))
      typeInto(dialogField(root, "javaClassName"), "1Bad")
      submit(root)
      for {
        _ <- eventually(dialogError(root) == "javaErrorClassName")
        _ = typeInto(dialogField(root, "javaClassName"), "Main")
        _ = submit(root)
        _ <- eventually(dialogError(root) == "javaErrorDuplicate")
        _ = typeInto(dialogField(root, "javaClassName"), "Helper")
        _ = submit(root)
        _ <- eventually(closed(root), clue = "class dialog closed")
        _ = assert(code(state).startsWith(source) && code(state).endsWith("\n\nclass Helper {\n}\n"), code(state))
        _ = assertEquals(viewStatus(root), "javaViewDiagram", "a new class keeps the open method")
        _ = assertEquals(heading(root), "Main.main")
        helper = classBox(root, "Helper")
        _ = assert(all(helper, ".java-uml__operation").isEmpty)
        _ = click(addButton(helper, "javaAddMethod"))
        _ = submit(root)
        _ <- eventually(dialogError(root) == "javaErrorName")
        _ = typeInto(dialogField(root, "javaName"), "greet")
        _ = typeInto(dialogField(root, "javaReturnType"), "Str ing;")
        _ = submit(root)
        _ <- eventually(dialogError(root) == "javaErrorType")
        _ = typeInto(dialogField(root, "javaReturnType"), "String")
        _ = click(buttonNamed(dialog(root), "javaAddParameter"))
        _ = submit(root)
        _ <- eventually(dialogError(root) == "javaErrorName", clue = "a parameter needs a name")
        _ = typeInto(one(dialog(root), ".java-function-editor__parameter-type"), "String")
        _ = typeInto(one(dialog(root), ".java-function-editor__parameter-name"), "name")
        _ = assertEquals(one(dialog(root), ".java-function-editor__preview .java-uml-line").textContent, "+ greet(name: String): String")
        _ = assert(withText(dialog(root), "label", "javaStatic").map(one(_, "input")).head.asInstanceOf[dom.html.Input].checked,
          "a class without object state suggests a static method")
        _ = click(withText(dialog(root), "label", "javaStatic").map(one(_, "input")).head)
        _ = submit(root)
        _ <- eventually(closed(root), clue = "method dialog closed")
      } yield {
        assert(code(state).contains("class Helper {\n\n  public String greet(String name) {"), code(state))
        assertEquals(heading(root), "Helper.greet")
        assertEquals(umlLine(root).textContent, "+ greet(name: String): String")
        assertEquals(viewStatus(root), "javaViewDiagram")
      }
    }
  }

  test("attributes, constructors and deletions change the source and the diagram together") {
    val counter = "public class Main {\n  public static void main(String[] args) {\n  }\n}\n\n" +
      "class Counter {\n  public void increment() {\n    \n  }\n}\n"
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, counter))
    mountedAsync(editor(state).getDomElement()) { root =>
      click(addButton(classBox(root, "Counter"), "javaAddAttribute"))
      typeInto(dialogField(root, "javaName"), "count")
      assertEquals(one(dialog(root), ".java-function-editor__preview .java-uml-line").textContent, "- count: int")
      submit(root)
      for {
        _ <- eventually(closed(root), clue = "attribute dialog closed")
        _ = assert(code(state).contains("class Counter {\n  private int count;\n\n  public void increment()"), code(state))
        _ = assertEquals(all(classBox(root, "Counter"), ".java-uml__attribute").map(_.textContent), List("- count: int"))
        _ = click(addButton(classBox(root, "Counter"), "javaAddConstructor"))
        _ = click(buttonNamed(dialog(root), "javaAddParameter"))
        _ = typeInto(one(dialog(root), ".java-function-editor__parameter-name"), "count")
        _ = submit(root)
        _ <- eventually(closed(root), clue = "constructor dialog closed")
        _ = assert(code(state).contains("  public Counter(int count) {\n    this.count = count;\n  }\n\n  public void increment()"), code(state))
        _ = assertEquals(heading(root), "Counter.Counter")
        _ = assertEquals(umlLine(root).textContent, "+ Counter(count: int)")
        _ = click(one(classBox(root, "Counter"), "button.java-uml__attribute[data-member='count']"))
        _ = assertEquals(valueOf(dialogField(root, "javaName")), "count")
        _ = typeInto(dialogField(root, "javaType"), "long")
        _ = submit(root)
        _ <- eventually(closed(root), clue = "attribute saved")
        _ = assert(code(state).contains("  private long count;\n"), code(state))
        _ = click(operation(root, "increment"))
        _ = click(one(root, "button[data-action='delete-member']"))
        _ = assertEquals(one(dialog(root), ".java-uml-line").textContent, "+ increment()")
        _ = submit(root)
        _ <- eventually(closed(root), clue = "deletion confirmed")
        _ = assert(!code(state).contains("increment"), code(state))
        _ = assertEquals(heading(root), "javaChooseMethod")
        _ = click(one(classBox(root, "Counter"), "button.java-uml__attribute[data-member='count']"))
        _ = click(buttonNamed(dialog(root), "javaDelete"))
        _ = submit(root)
        _ <- eventually(closed(root), clue = "attribute deleted")
      } yield {
        assert(!code(state).contains("private long count"), code(state))
        assertEquals(all(classBox(root, "Counter"), ".java-uml__attribute").size, 0)
        assertEquals(all(classBox(root, "Counter"), ".java-uml__operation").map(_.textContent), List("+ Counter(count: int)"))
      }
    }
  }

  test("the procedural preset offers only new methods; relationships are listed for screen readers") {
    val program = "public class Main {\n  public static void main(String[] args) { new Dog(); }\n}\n" +
      "abstract class Animal { }\nclass Dog extends Animal { private Animal friend; }\n"
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, program))
    mounted(editor(state, features = JavaStructureFeatures.Procedural).getDomElement()) { root =>
      assertEquals(all(root, ".java-uml__add").map(_.textContent).distinct, List("+ javaAddMethod"))
      assertEquals(all(root, ".java-uml__row").map(row => all(row, ".java-uml__class-name").map(_.textContent)),
        List(List("Main", "Animal"), List("Dog")), "subclasses are drawn below their superclass")
      assert(one(classBox(root, "Animal"), ".java-uml__class-name").classList.contains("java-uml__class-name--abstract"))
      assertEquals(all(root, ".java-uml__relation-list li").map(_.textContent), List(
        "Main javaUses Dog", "Dog javaInherits Animal", "Dog javaKnows Animal (friend)"))
      assertEquals(all(root, ".java-uml__relations-layer marker").size, 2)
    }
  }

  test("dialogs close on cancel; a state that is not Java is reported and locks the structure tools") {
    val state = Var[ProgrammingState](ProgrammingStateRaw(Java, source))
    mounted(editor(state).getDomElement()) { root =>
      click(addButton(one(root, ".java-uml__heading"), "javaAddClass"))
      click(buttonNamed(dialog(root), "javaCancel"))
      assertEquals(all(root, "dialog").size, 0)
      click(addButton(classBox(root, "Main"), "javaAddMethod"))
      dispatch(dialog(root), "cancel")
      assertEquals(all(root, "dialog").size, 0, "Escape closes the dialog")
      assertEquals(code(state), source)

      state.set(ProgrammingStateRaw(Python, "forward(10)\n"))
      assert(one(root, "[role=alert]").textContent.contains("javaNotJava"))
      assert(isDisabled(one(root, "button[data-view='source']")) && isDisabled(one(root, "button[data-view='diagram']")))
      assert(root.textContent.contains("javaNoClasses"))
      state.set(ProgrammingStateRaw(Java, source.replace("forward(10)", "forward(70)")))
      assertEquals(all(root, "[role=alert]").filter(_.textContent.contains("javaNotJava")).size, 0)
      assertEquals(viewStatus(root), "javaViewSource", "the selection was lost while the state was not Java")
      click(operation(root, "main"))
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
        assertEquals(viewStatus(root), "javaViewSource")
        click(operation(root, "draw"))
        assertEquals(heading(root), "Main.draw")
      }
      assertEquals(listeners.now(), 0, "unmounting stops listening to the screen size")
    } finally js.special.delete(window, "matchMedia")
  }
}
