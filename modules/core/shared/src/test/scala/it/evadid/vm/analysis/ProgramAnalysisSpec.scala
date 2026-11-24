package it.evadid.vm.analysis

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

class ProgramAnalysisSpec extends FunSuite {
  test("Python runs as written; other languages are converted to Python for dynamic execution") {
    val python = "forward(10)\nprint('x', end='')\n"
    assertEquals(ProgramAnalysis.of(ProgrammingStateRaw(Python, python)).python, Right(python))
    val java = ProgramAnalysis.of(ProgrammingStateJavaStructured("class Main { static void main(String[] a) { System.out.println(1); } }"))
    assert(java.python.exists(_.contains("print(1)")), java.python)
    assert(ProgramAnalysis.of(ProgrammingStateRaw(Java, "class {")).python.isLeft)
  }

  test("static facts come from the shared BeExpression") {
    val drawing = ProgramAnalysis.of(ProgrammingStateRaw(Python, "for _ in range(2):\n    forward(10)\n    right(90)\n"))
    assertEquals(drawing.turtleCommands.map(_.size), Right(4))
    assertEquals(drawing.unknownDependency, None)
    val unknown = ProgramAnalysis.of(ProgrammingStateRaw(Python, "helper(3)\n"))
    assertEquals(unknown.unknownDependency.map(_.definedFunctions.map(_.functionTypeInfo.displayName.spelling())), Some(List("helper")))
  }

  test("unsupported source blocks the static drawing but not dynamic execution") {
    val analysis = ProgramAnalysis.of(ProgrammingStateRaw(Python, "print('a', end='')\nforward(1)\n"))
    assertEquals(analysis.unsupportedSource.size, 1)
    assert(analysis.turtleCommands.isLeft)
    assert(analysis.python.isRight)
    assertEquals(upickle.default.read[ProgramAnalysis](upickle.default.write(analysis)), analysis)
  }
}
