package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage


import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppShapeComposition
import it.evadid.core.datastructures.vectorShapes.atomar.AppShapeDrawingRoutineElement
import it.evadid.core.datastructures.vectorShapes.renderer.VmToSvg
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.util.logging.Logger
import it.evadid.vm.BeProgram
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.types.BeDataValueLiteral
import it.evadid.vm.conversion.snapexpression.SnapControlFlow
import it.evadid.workbook.elements.interactionElements.programming.state.snap
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog
import it.evadid.vm.simulation.turtle.TurtleProgramAnalysis
import munit.FunSuite

class TurtleProgramAnalysisSpec extends FunSuite {

  private val logger = Logger.withNameAndPrefixes(Some("TurtleProgramAnalysisSpec"))

  test("catalog aliases map to turtle commands") {
    assertEquals(SnapTurtleCatalog.snapSelectorByPythonName("right"), "turn")
    assertEquals(snap.SnapTurtleCatalog.snapSelectorByPythonName("penup"), "up")
    assertEquals(snap.SnapTurtleCatalog.turtleCommandByPythonName("turn_left"), "left")
  }

  test("forward and repeat unroll into turtle commands") {
    val program = SourceParsing.parse("""for _ in range(2):
        |    forward(10)
        |""".stripMargin, SourceLanguage.Python).parsed().program
    val commands = TurtleProgramAnalysis(program.fullProgram).completedCommands
    assertEquals(commands.map(_.name.universalInterpretation()), List("forward", "forward"))
    assertEquals(commands.map(_.args), List(List(10.0), List(10.0)))
  }

  test("quoted text preserves digits and inner quotes") {
    val program = SourceParsing.parse("""color("#112233")
        |color("bird's blue")
        |color('say "blue"')
        |color('123')
        |""".stripMargin, SourceLanguage.Python).parsed().program
    assertEquals(
      TurtleProgramAnalysis(program.fullProgram).completedCommands,
      List("#112233", "bird's blue", "say \"blue\"", "123")
        .map(value => TurtleCommand.Color[Double](value))
    )
  }

  test("scientific numeric literals produce only numeric arguments") {
    val program = SourceParsing.parse("forward(1e3)\nforward(2.5e2)", SourceLanguage.Python).parsed().program
    assertEquals(
      TurtleProgramAnalysis(program.fullProgram).completedCommands,
      List(TurtleCommand.Forward[Double](1000.0), TurtleCommand.Forward[Double](250.0))
    )
  }

  test("raw non-numeric literals retain their textual payload") {
    val call = SnapControlFlow.topLevelStatements(SourceParsing.parse("color('red')", SourceLanguage.Python).parsed().program.fullProgram)
      .collectFirst { case call: BeFunctionCall => call }.get
    List("NaN", "Infinity").foreach { text =>
      val literalCall = call.copy(parameterValueMap = call.parameterValueMap.map { (parameter, _) =>
        parameter -> BeUseValue(BeDataValueLiteral(text), None)
      })
      assertEquals(TurtleProgramAnalysis(literalCall).completedCommands, List(TurtleCommand.Color[Double](text)))
    }
  }

  test("boolean stitch arguments stay separate from numeric arguments") {
    val program = SourceParsing.parse("jump_stitch(True)\njump_stitch(False)\ncross_stitch(8, 13, False)", SourceLanguage.Python).parsed().program
    assertEquals(
      TurtleProgramAnalysis(program.fullProgram).completedCommands,
      List(
        TurtleCommand.fromText[Double]("jump_stitch", stringArgs = List("True")),
        TurtleCommand.fromText[Double]("jump_stitch", stringArgs = List("False")),
        TurtleCommand.fromText[Double]("cross_stitch", List(8.0, 13.0), List("False"))
      )
    )
  }

  test("color, running stitch, and arithmetic for-loop produce a path") {
    val program = SourceParsing.parse("""running_stitch(10)
        |color("red")
        |for i in range(1, 4 + 1):
        |    forward(i * 10)
        |    turn(90)
        |""".stripMargin, SourceLanguage.Python).parsed().program
    val commands = TurtleProgramAnalysis(program.fullProgram).completedCommands
    assert(commands.exists(_.name.universalInterpretation() == "running_stitch"), clue = commands.map(_.name.universalInterpretation()))
    assert(commands.exists(_.name.universalInterpretation() == "color"), clue = commands.map(_.name.universalInterpretation()))
    assertEquals(commands.count(_.name.universalInterpretation() == "forward"), 4)
    val builder = TurtleProgramAnalysis.toPathBuilder(program.fullProgram)
    assert(builder.svgPathBuilder.toSvgPathD.nonEmpty)
    assertEquals(builder.turtleState.headingDeg, 90.0)
  }

  test("Snap preview heading starts at 90 degrees") {
    val builder = TurtlePathBuilder[Double](Point(0.0, 0.0), List(TurtleCommand.Forward(50.0)), 90)
    assertEquals(builder.turtleState.x, 0.0)
    assertEquals(builder.turtleState.y, -50.0)
  }

  test("VmToSvg renders overlaid path segments instead of the duck stub") {
    val program = SourceParsing.parse("forward(40)\nturn(90)\nforward(40)", SourceLanguage.Python).parsed().program
    val shape = VmToSvg.renderBeExpression(logger, program.fullProgram)
    assert(shape.isInstanceOf[AppShapeComposition[Double]], clue = shape.getClass.getName)
    assert(shape.childrenInRenderingOrder.forall(_.isInstanceOf[AppShapeDrawingRoutineElement[Double]]))
    assert(shape.childrenInRenderingOrder.nonEmpty)
  }

  test("VmToSvg splits styled segments by pen color and width") {
    val program = SourceParsing.parse("""color("red")
        |pensize(2)
        |forward(10)
        |color("blue")
        |pensize(4)
        |forward(10)
        |""".stripMargin, SourceLanguage.Python).parsed().program
    val shape = VmToSvg.renderBeExpression(logger, program.fullProgram)
    val children = shape.childrenInRenderingOrder.collect { case path: AppShapeDrawingRoutineElement[Double] => path }
    assertEquals(children.size, 2)
    assertEquals(children.head.config.colorStroke.toRGB, it.evadid.core.datastructures.color.RGBColor.red)
    assertEquals(children.head.config.strokeWidth, 2.0)
    assertEquals(children(1).config.colorStroke.toRGB, it.evadid.core.datastructures.color.RGBColor.blue)
    assertEquals(children(1).config.strokeWidth, 4.0)
    assert(children.forall(!_.config.fillEnabled))
    assertEquals(children.head.minSize, children(1).minSize)
  }

  test("custom def calls are interpreted") {
    val program = SourceParsing.parse("""def square(n):
        |    forward(n)
        |    turn(90)
        |square(20)
        |""".stripMargin, SourceLanguage.Python).parsed().program
    val commands = TurtleProgramAnalysis(program.fullProgram).completedCommands
    assertEquals(commands.map(_.name.universalInterpretation()), List("forward", "right"))
    assertEquals(commands.head.args, List(20.0))
  }
  test("recursive calls restore their caller's parameters and local variables") {
    val program = SourceParsing.parse("""def branch(length, depth):
        |    if depth == 0:
        |        forward(length)
        |    else:
        |        branch(length / 2, depth - 1)
        |        branch(length / 2, depth - 1)
        |branch(80, 2)
        |forward(7)
        |""".stripMargin, SourceLanguage.Python).parsed().program
    val commands = TurtleProgramAnalysis(program.fullProgram).completedCommands
    assertEquals(commands.filter(_.name.universalInterpretation() == "forward").map(_.args),
      List(List(20.0), List(20.0), List(20.0), List(20.0), List(7.0)))
  }
  test("modulo drives alternating shapes and every-fourth branches") {
    for (period <- List(2, 4)) {
      val program = SourceParsing.parse(s"""for i in range(1, 8):
        |    if i % $period == 0:
        |        forward(40)
        |    else:
        |        forward(10)
        |""".stripMargin, SourceLanguage.Python).parsed().program
      val commands = TurtleProgramAnalysis(program.fullProgram).completedCommands
      assertEquals(commands.map(_.args.head), (1 to 7).map(i => if i % period == 0 then 40.0 else 10.0).toList)
    }
  }

  test("remainder agrees with Python for signed integer and fractional operands") {
    val program = SourceParsing.parse("negative_divisor = -3\nforward(-5 % 3)\nforward(5 % negative_divisor)\nforward(5.5 % 2)\n", SourceLanguage.Python).parsed().program
    assertEquals(TurtleProgramAnalysis(program.fullProgram).completedCommands.map(_.args),
      List(List(1.0), List(-1.0), List(1.5)))
  }


  private def drawn(python: String) = {
    val info = TurtleProgramAnalysis(SourceParsing.parse(python, SourceLanguage.Python).parsed().program.fullProgram)
    (info.executionStatus, info.executedTurtleCommandsInOrder)
  }

  test("a function's return value feeds the calling statement, and its own drawing comes first") {
    import TurtleCommand.{Forward, Left}
    val (status, commands) = drawn("def step(n: float) -> float:\n    left(10)\n    return n * 2\n\nforward(step(3))\n")
    assertEquals(status, it.evadid.vm.simulation.turtle.TurtleExecutionStatus.Completed)
    assertEquals(commands, List[TurtleCommand[Double]](Left(10.0), Forward(6.0)))
  }

  test("return ends the function, also from inside loops, and a bare return ends a command function") {
    import TurtleCommand.Forward
    assertEquals(drawn("def first(limit: float) -> float:\n    for i in range(10):\n        forward(1)\n        if i == limit:\n            return i\n    return -1\n\nforward(first(2))\n")._2,
      List[TurtleCommand[Double]](Forward(1.0), Forward(1.0), Forward(1.0), Forward(2.0)))
    assertEquals(drawn("def maybe(n: float) -> None:\n    if n > 3:\n        return\n    forward(n)\n\nmaybe(5)\nmaybe(2)\n")._2,
      List[TurtleCommand[Double]](Forward(2.0)))
  }

  test("returned values work in conditions, loop bounds, assignments and recursion") {
    import TurtleCommand.Forward
    val python = "def fact(n: float) -> float:\n    if n <= 1:\n        return 1\n    return n * fact(n - 1)\n\ndef big(n: float) -> bool:\n    return n > 5\n\n" +
      "size = fact(3)\nfor _ in range(fact(2)):\n    forward(size)\nwhile big(size):\n    size = size - 1\nforward(size)\n"
    assertEquals(drawn(python)._2, List[TurtleCommand[Double]](Forward(6.0), Forward(6.0), Forward(5.0)))
  }
}
