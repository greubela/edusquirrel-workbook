package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingStateSnapXmlHelper

import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapPythonPopup
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.vm.BeProgram
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateSnapXml
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{SnapCanvasLayout, SnapCanvasScript}
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
    val scripts = SnapPythonPopup.scriptsOf(ProgrammingStateSnapXmlHelper.fromProgram(program, layout))
    assertEquals(scripts.size, 2)
    assertEquals(scripts(0).x, 70)
    assertEquals(scripts(1).x, 200)
    assert(scripts(0).python.nonEmpty)
    assert(scripts(1).python.nonEmpty)
  }

  test("overview examples include def user functions") {
    assert(SnapPythonPopup.OverviewExamples.exists(_.contains("def ")), clue = SnapPythonPopup.OverviewExamples)
  }

  test("Python apply replaces project XML while preserving legacy floating metadata") {
    val source = ProgrammingStateSnapXml(ProgrammingStateSnapXmlHelper.mini.snapXml,
      legacyFloatingObjects = List(" watcher ", "comment\r\n\t "))
    val next = SnapPythonPopup.applyPython(source, "forward(12)").fold(message => fail(message), identity)
    assertNotEquals(next.snapXml, source.snapXml)
    assertEquals(next.legacyFloatingObjects, source.legacyFloatingObjects)
    assertEquals(next.toBeExpressionState.deriveTurtleCommands,
      List(TurtleCommand[Double]("forward", List(12.0))))
    assertEquals(ProgrammingExercise.StateSerializer.deserialize(
      ProgrammingExercise.StateSerializer.serialize(next)), next)
  }

  test("invalid Python apply leaves the stored Snap project and metadata intact") {
    val source = ProgrammingStateSnapXml(ProgrammingStateSnapXmlHelper.mini.snapXml,
      legacyFloatingObjects = List(" watcher ", "comment\nline two"))
    val stored = ProgrammingExercise.StateSerializer.serialize(source)
    assert(SnapPythonPopup.applyPython(source, "forward(").isLeft)
    assertEquals(ProgrammingExercise.StateSerializer.serialize(source), stored)
  }
}
