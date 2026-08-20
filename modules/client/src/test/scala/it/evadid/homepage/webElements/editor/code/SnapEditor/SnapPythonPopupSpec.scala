package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.vm.BeProgram
import it.evadid.workbook.elements.interactionElements.programming.{
  ProgrammingExerciseState,
  SnapCanvasLayout,
  SnapCanvasScript
}
import munit.FunSuite

/**
 * FEATURE: SnapPythonPopup — delete with SnapPythonPopup.scala when removing the button.
 */
class SnapPythonPopupSpec extends FunSuite {

  test("scriptsOf splits flat calls by layout into multiple views") {
    val program = BeProgram.miniProgram() // two forward(100) calls
    val layout = SnapCanvasLayout(
      List(
        SnapCanvasScript(70, 80, 1),
        SnapCanvasScript(200, 150, 1)
      )
    )
    val scripts = SnapPythonPopup.scriptsOf(ProgrammingExerciseState.fromProgram(program, layout))
    assertEquals(scripts.size, 2)
    assertEquals(scripts(0).x, 70)
    assertEquals(scripts(1).x, 200)
    assert(scripts(0).python.nonEmpty)
    assert(scripts(1).python.nonEmpty)
  }
}
