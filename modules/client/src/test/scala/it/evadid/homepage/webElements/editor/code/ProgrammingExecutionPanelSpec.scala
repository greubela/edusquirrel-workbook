package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class ProgrammingExecutionPanelSpec extends EditorDomSuite {
  Seq[ProgrammingState](
    ProgrammingStateJavaStructured("class Main { static void main(String[] args) { System.out.println(\"hello\"); System.out.println(); Turtle.forward(10); } }"),
    ProgrammingStateRaw(Python, "print(\"hello\")\nprint()\nforward(10)\n")
  ).foreach { program =>
    test(s"${program.associatedLanguage} executes through the shared panel with exact console newlines") {
      val source = Var(program)
      val panel = new ProgrammingExecutionPanel(source.signal, key => Val(key)).getDomElement()
      val close = mount(panel)
      val run = panel.ref.querySelector("button").asInstanceOf[dom.HTMLButtonElement]
      eventually(!run.disabled, "conversion checked asynchronously").flatMap { _ =>
        run.click()
        eventually(panel.ref.querySelector("[role=log]").textContent == "hello\n\n", panel.ref.textContent)
      }.flatMap { _ =>
        source.set(ProgrammingStateRaw(Python, "???"))
        eventually(run.disabled && !panel.ref.querySelector("[role=status]").hasAttribute("hidden"), "unusable source disables execution")
      }.andThen(_ => close())
    }
  }
}
