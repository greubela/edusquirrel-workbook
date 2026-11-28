package it.evadid.vm.conversion.snappython

import it.evadid.workbook.interactions.programming.state.ProgrammingStateSnapScalaModel
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.vm.conversion.expressionsnap.ExpressionToSnap
import it.evadid.vm.conversion.pythonsnap.PythonToSnap

import it.evadid.workbook.interactions.programming.state.{ProgrammingState, ProgrammingStateRaw}

import it.evadid.vm.BeProgram
import it.evadid.workbook.interactions.programming.state.snap.{SnapCanvasLayout, SnapCanvasScript}
import munit.FunSuite

class SnapProgramDerivationSpec extends FunSuite {

  test("scriptsOf splits flat calls by layout into multiple views") {
    val program = BeProgram.miniProgram() // two forward(100) calls
    val layout = SnapCanvasLayout(
      List(
        SnapCanvasScript(70, 80, 1),
        SnapCanvasScript(200, 150, 1)
      )
    )
    val scripts = SnapProgramDerivation.scriptsOf(ExpressionToSnap.fromProgram(program, layout))
    assertEquals(scripts.size, 2)
    assertEquals(scripts(0).x, 70)
    assertEquals(scripts(1).x, 200)
    assert(scripts(0).python.nonEmpty)
    assert(scripts(1).python.nonEmpty)
  }

  test("invalid Python apply leaves the stored Snap project and metadata intact") {
    val source = ProgrammingStateRaw(SnapLanguage, ProgrammingStateSnapScalaModel.mini.toSnapXml.code)
    val stored = ProgrammingState.serializer.serialize(source)
    assert(PythonToSnap.replacing(source, "forward(").isLeft)
    assertEquals(ProgrammingState.serializer.serialize(source), stored)
  }
}
