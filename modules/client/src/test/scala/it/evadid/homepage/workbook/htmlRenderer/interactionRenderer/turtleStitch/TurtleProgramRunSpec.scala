package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import org.scalajs.dom

class TurtleProgramRunSpec extends EditorDomSuite {
  test("typing leaves the drawing unchanged until Run; closing publishes the last draft") {
    val initial = ProgrammingStateRaw(Python, "forward(10)")
    val edited = ProgrammingStateRaw(Python, "forward(20)")
    val finalDraft = ProgrammingStateRaw(Python, "forward(30)")
    val answer = Var[ProgrammingState](initial)
    val graded = Var(Vector.empty[ProgrammingState])
    val run = TurtleProgramRun(answer, () => graded.update(_ :+ answer.now()))
    val close = mount(run.element(id => Val(id.entryKey)))
    try {
      answer.set(edited)
      assertEquals(run.executed.now(), initial: ProgrammingState)
      assertEquals(graded.now(), Vector.empty[ProgrammingState])
      dom.document.querySelector(".turtle-run button").asInstanceOf[dom.html.Button].click()
      assertEquals(run.executed.now(), edited: ProgrammingState)
      assertEquals(graded.now(), Vector[ProgrammingState](edited))
      answer.set(finalDraft)
    } finally close()
    assertEquals(run.executed.now(), finalDraft: ProgrammingState)
    assertEquals(graded.now(), Vector[ProgrammingState](edited, finalDraft))
  }
}
