package it.evadid.homepage.webElements.editor.code.PythonEditor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.homepage.webElements.editor.code.EvaTestContext
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorLabels
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonStructured
import scala.scalajs.js
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class PythonComponentEditorSpec extends UiDomSuite {
  override protected def jsdomGlobals: List[String] = super.jsdomGlobals :+ "HTMLDialogElement"
  private val dialogs = EditorDomSuite.window.HTMLDialogElement.prototype
  override def beforeAll(): Unit = {
    super.beforeAll()
    dialogs.updateDynamic("showModal")(((self: js.Dynamic) => self.setAttribute("open", "")): js.ThisFunction0[js.Dynamic, Unit])
    dialogs.updateDynamic("close")(((self: js.Dynamic) => self.removeAttribute("open")): js.ThisFunction0[js.Dynamic, Unit])
  }
  override def afterAll(): Unit = {
    js.special.delete(dialogs, "showModal"); js.special.delete(dialogs, "close")
    super.afterAll()
  }

  private lazy val english = upickle.default.read[Map[String,String]](js.Dynamic.global.selectDynamic("require")("fs")
    .readFileSync("resources/languageMaps/eva/blockeditor/map-en.json", "utf8").asInstanceOf[String]).map((key,value) => key.toLowerCase -> value)
  private lazy val labels = EvaEditorLabels.fromContent(id => Val(english.getOrElse(id.entryKey, id.fullId)))

  test("Python opens main, retains unfinished method edits, and full mode keeps the classes") {
    val state = Var(ProgrammingStatePythonStructured("def helper():\n    return 2\ndef main():\n    forward(10)\nmain()\nclass Box:\n    pass\n"))
    val component = PythonComponentEditor(state, EvaTestContext(labels), CodeMirrorEditorConfig.Default)
    mountedAsync(component.getDomElement()) { root =>
      def field = one(root, "textarea.code-mirror-editor__fallback")
      assert(valueOf(field).startsWith("def main():"))
      typeInto(field, "def main():\n    forward(")
      eventually(state.now().mainScript.contains("forward("), "the unfinished Python answer is retained").map { _ =>
        click(buttonNamed(root, "Full Mode"))
        assert(valueOf(field).contains("class Box:"))
        assert(valueOf(field).contains("def helper():"))
        assert(state.now().definedClasses.contains(BeEntityName.fromCodeString("Box")))
      }
    }
  }
  test("member dialogs validate names, add static functions and generate constructor assignments") {
    val state = Var(ProgrammingStatePythonStructured("print(1)"))
    mountedAsync(PythonComponentEditor(state, EvaTestContext(labels), CodeMirrorEditorConfig.Default).getDomElement()) { root =>
      def dialog = one(root, "dialog")
      def field(caption: String) = withText(dialog, "label", caption).map(one(_, "input")).head
      def box = all(root, ".uml-class").find(_.querySelector(".uml-class__header").textContent == "Box").get
      val key = BeEntityName.fromCodeString("Box")
      click(buttonNamed(root, "Add class"))
      typeInto(field("Name"), "class")
      click(buttonNamed(dialog, "Add class"))
      assert(one(dialog, "[role=alert]").textContent.nonEmpty)
      typeInto(field("Name"), "Box")
      click(buttonNamed(dialog, "Add class"))
      click(buttonNamed(box, "Add function"))
      typeInto(field("Name"), "size")
      typeInto(field("Parameters"), "n: float")
      typeInto(field("Type (optional)"), "float")
      click(one(dialog, "input[type=checkbox]"))
      click(buttonNamed(dialog, "Add function"))
      assert(state.now().definedClasses(key).contains("@staticmethod\n    def size(n: float) -> float:"))
      click(buttonNamed(box, "Add attribute"))
      typeInto(field("Name"), "count")
      typeInto(field("Type (optional)"), "int")
      typeInto(field("Initial value"), "5")
      click(buttonNamed(dialog, "Add attribute"))
      assert(state.now().definedClasses(key).contains("count: int = 5"))
      click(buttonNamed(box, "Add constructor"))
      typeInto(field("Parameters"), "value: float")
      click(buttonNamed(dialog, "Add constructor"))
      assert(state.now().definedClasses(key).contains("self.value = value"))
      assertEquals(state.now().mainScript, "print(1)")
      Future.unit
    }
  }

}
