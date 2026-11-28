package it.evadid.vm.analysis

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.workbook.interactions.programming.state.*
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
    assertEquals(drawing.drawing.map(_.size), Right(4))
    assertEquals(drawing.unknownDependency, None)
    val unknown = ProgramAnalysis.of(ProgrammingStateRaw(Python, "helper(3)\n"))
    assertEquals(unknown.unknownDependency.map(_.definedFunctions.map(_.functionTypeInfo.displayName.spelling())), Some(List("helper")))
  }

  test("the turtle run keeps the commands of an early stop with its status") {
    val analysis = ProgramAnalysis.of(ProgrammingStateRaw(Python, "forward(10)\nwhile True:\n    right(1)\n"))
    val run = analysis.turtle.toOption.get
    assertEquals(run.status, it.evadid.vm.simulation.turtle.TurtleExecutionStatus.LimitExceeded)
    assertEquals(run.commands.head, it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.Forward(10.0))
    assert(analysis.completedCommands.isLeft)
    assertEquals(upickle.default.read[ProgramAnalysis](upickle.default.write(analysis)), analysis)
  }

  test("unknown calls, reads and writes in Java are located by the parser") {
    val source = "class Main {\n  static void main(String[] a) {\n    System.out2.println(1);\n    Object x = Config.screen.limit;\n" +
      "    Config.screen.limit = 3;\n    helper(2);\n    System.out.println(x);\n  }\n}"
    def located(state: ProgrammingState) = ProgramAnalysis.of(state).warnings.map(warning =>
      (warning.subject, warning.startLine, warning.startColumn, warning.endLine, warning.endColumn))
    val expected = Seq(("System.out2.println", 3, 5, 3, 24), ("Config.screen.limit", 4, 16, 4, 35),
      ("Config.screen.limit", 5, 5, 5, 24), ("helper", 6, 5, 6, 11))
    assertEquals(located(ProgrammingStateRaw(Java, source)), expected)
    assertEquals(located(ProgrammingStateJavaStructured(source)), expected)
    assertEquals(ProgramAnalysis.of(ProgrammingStateRaw(Python, "helper(3)\n")).warnings.map(w => (w.subject, w.startLine, w.startColumn, w.endColumn)), Seq(("helper", 1, 1, 7)))
    val unreadable = ProgramAnalysis.of(ProgrammingStateRaw(Java, "class Main {\n  static void main(String[] a) {\n    int x = 1;\n" +
      "    assert x > 0;\n  }\n}")).warnings
    assertEquals(unreadable.map(warning => (warning.kind, warning.startLine)), Seq((SourceWarning.Kind.Unreadable, 4)))
  }

  test("unsupported source blocks the static drawing but not dynamic execution") {
    val analysis = ProgramAnalysis.of(ProgrammingStateRaw(Python, "print('a', end='')\nforward(1)\n"))
    assertEquals(analysis.unsupportedSource.size, 1)
    assert(analysis.drawing.isLeft)
    assert(analysis.completedCommands.isLeft, "the interpreter stops at the unsupported line, as graders see it")
    assert(analysis.python.isRight)
    assertEquals(upickle.default.read[ProgramAnalysis](upickle.default.write(analysis)), analysis)
  }
}
