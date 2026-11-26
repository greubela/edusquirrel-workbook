package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

class ProgrammingStateViewSpec extends FunSuite {
  private def pythonText(answer: Var[ProgrammingState]) = ProgrammingStateView(answer, inactive = "") {
    case ProgrammingStateRaw(Python, code) => code
  }(ProgrammingStateRaw(Python, _))

  test("an active view reads and writes the answer in its representation") {
    val answer = Var[ProgrammingState](ProgrammingStateRaw(Python, "print(1)"))
    val text = pythonText(answer)
    assertEquals(text.now(), "print(1)")
    text.set("print(2)")
    assertEquals(answer.now(), ProgrammingStateRaw(Python, "print(2)"): ProgrammingState)
  }

  test("after a switch to another language the outgoing view cannot overwrite the converted answer") {
    val answer = Var[ProgrammingState](ProgrammingStateRaw(Python, "print(1)"))
    val text = pythonText(answer)
    val converted = ProgrammingStateRaw(Java, "class Main {}")
    answer.set(converted)
    assertEquals(text.now(), "", "the inactive placeholder")
    text.set("")
    assertEquals(answer.now(), converted: ProgrammingState)
  }
}
