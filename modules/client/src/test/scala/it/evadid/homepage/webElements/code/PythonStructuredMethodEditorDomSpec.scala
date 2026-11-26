package it.evadid.homepage.webElements.code

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonStructured
import org.scalajs.dom
import scala.concurrent.ExecutionContext

/** The Python structure editor: the module box, class boxes and the dialogs writing Python. */
class PythonStructuredMethodEditorDomSpec extends UiDomSuite {
  private given ExecutionContext = ExecutionContext.global
  override protected def jsdomGlobals: List[String] = super.jsdomGlobals :+ "HTMLDialogElement"
  private val logger = Logger.withNameAndPrefixes(Some("python-editor-test"), PrintToStdLogger.printNothing)
  private val source = "import turtle\n\nclass Pen:\n    def __init__(self, width: float):\n        self.width = width\n\n" +
    "def main():\n    turtle.forward(10)\n\nmain()"

  private def heading(root: dom.Element) = one(root, ".java-function-editor__workspace-heading h3").textContent
  private def dialog(root: dom.Element) = one(root, "dialog")
  private def dialogField(root: dom.Element, caption: String) = withText(dialog(root), "label", caption).map(one(_, "input")).head
  private def closed(root: dom.Element) = all(root, "dialog").isEmpty
  private def addButton(box: dom.Element, label: String) =
    withText(box, ".java-uml__add", label).headOption.getOrElse(fail(s"no add button '$label' in ${box.textContent}"))

  test("main opens first; the module box lists functions; a new function and attribute are written as Python") {
    val state = Var(ProgrammingStatePythonStructured(source))
    val labels = JavaEditorLabels(key => Val(key), python = true)
    mountedAsync(new PythonStructuredMethodEditor(logger, state, labels = labels).getDomElement()) { root =>
      val module = one(root, ".java-uml__class--module")
      assert(module.textContent.contains("«module»"), module.textContent)
      assertEquals(all(module, ".java-uml__operation").map(_.textContent), List("+ main()"))
      assertEquals(heading(root), "main")
      val pen = one(root, ".java-uml__class[data-class-name='Pen']")
      assertEquals(all(pen, ".java-uml__attribute").map(_.textContent), List("+ width: float"))
      assertEquals(all(pen, ".java-uml__operation").map(_.textContent), List("+ __init__(width: float)"))
      click(addButton(module, "pythonAddFunction"))
      assert(withText(dialog(root), "label", "pythonStatic").isEmpty, "module functions are not static")
      typeInto(dialogField(root, "javaName"), "square")
      click(buttonNamed(dialog(root), "javaAddParameter"))
      typeInto(one(dialog(root), ".java-function-editor__parameter-name"), "side")
      click(one(dialog(root), "button[data-action='submit']"))
      for {
        _ <- eventually(closed(root), clue = "function dialog closed")
        _ = assert(state.now().toString.contains("def main():\n    turtle.forward(10)\n\ndef square(side: float):\n    pass"), state.now().toString)
        _ = assertEquals(heading(root), "square")
        _ = click(addButton(one(root, ".java-uml__class[data-class-name='Pen']"), "javaAddAttribute"))
        _ = typeInto(dialogField(root, "javaName"), "color")
        _ = typeInto(dialogField(root, "pythonType"), "str")
        _ = click(one(dialog(root), "button[data-action='submit']"))
        _ <- eventually(closed(root), clue = "attribute dialog closed")
      } yield {
        assert(state.now().toString.contains("        self.width = width\n        self.__color: str = \"\""), state.now().toString)
        assert(all(root, ".java-uml__attribute").exists(_.textContent == "- __color: str"))
      }
    }
  }
}
