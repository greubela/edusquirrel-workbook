package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppShapeComposition
import it.evadid.core.datastructures.vectorShapes.atomar.AppShapeDrawingRoutineElement
import it.evadid.core.datastructures.vectorShapes.renderer.VmToSvg
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.util.logging.Logger
import it.evadid.vm.BeProgram
import it.evadid.workbook.elements.interactionElements.programming.state.snap
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog
import munit.FunSuite

class BeExpressionToTurtleCommandsSpec extends FunSuite {

  private val logger = Logger.withNameAndPrefixes(Some("BeExpressionToTurtleCommandsSpec"))

  test("catalog aliases map to turtle commands") {
    assertEquals(SnapTurtleCatalog.snapSelectorByPythonName("right"), "turn")
    assertEquals(snap.SnapTurtleCatalog.snapSelectorByPythonName("penup"), "up")
    assertEquals(snap.SnapTurtleCatalog.turtleCommandByPythonName("turn_left"), "left")
  }

  test("forward and repeat unroll into turtle commands") {
    val program = BeProgram.fromPythonString(
      """for _ in range(2):
        |    forward(10)
        |""".stripMargin
    )
    val commands = BeExpressionToTurtleCommands(program.fullProgram)
    assertEquals(commands.map(_.name), List("forward", "forward"))
    assertEquals(commands.map(_.args), List(List(10.0), List(10.0)))
  }

  test("color, running stitch, and arithmetic for-loop produce a path") {
    val program = BeProgram.fromPythonString(
      """running_stitch(10)
        |color("red")
        |for i in range(1, 4 + 1):
        |    forward(i * 10)
        |    turn(90)
        |""".stripMargin
    )
    val commands = BeExpressionToTurtleCommands(program.fullProgram)
    assert(commands.exists(_.name == "running_stitch"), clue = commands.map(_.name))
    assert(commands.exists(_.name == "color"), clue = commands.map(_.name))
    assertEquals(commands.count(_.name == "forward"), 4)
    val builder = BeExpressionToTurtleCommands.toPathBuilder(program.fullProgram)
    assert(builder.svgPathBuilder.toSvgPathD.nonEmpty)
    assertEquals(builder.turtleState.headingDeg, 90.0)
  }

  test("Snap preview heading starts at 90 degrees") {
    val builder = TurtlePathBuilder[Double](Point(0.0, 0.0), List(TurtleCommand("forward", List(50.0))), 90)
    assertEquals(builder.turtleState.x, 0.0)
    assertEquals(builder.turtleState.y, -50.0)
  }

  test("VmToSvg renders overlaid path segments instead of the duck stub") {
    val program = BeProgram.fromPythonString("forward(40)\nturn(90)\nforward(40)")
    val shape = VmToSvg.renderBeExpression(logger, program.fullProgram)
    assert(shape.isInstanceOf[AppShapeComposition[Double]], clue = shape.getClass.getName)
    assert(shape.childrenInRenderingOrder.forall(_.isInstanceOf[AppShapeDrawingRoutineElement[Double]]))
    assert(shape.childrenInRenderingOrder.nonEmpty)
  }

  test("VmToSvg splits styled segments by pen color and width") {
    val program = BeProgram.fromPythonString(
      """color("red")
        |pensize(2)
        |forward(10)
        |color("blue")
        |pensize(4)
        |forward(10)
        |""".stripMargin
    )
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
    val program = BeProgram.fromPythonString(
      """def square(n):
        |    forward(n)
        |    turn(90)
        |square(20)
        |""".stripMargin
    )
    val commands = BeExpressionToTurtleCommands(program.fullProgram)
    assertEquals(commands.map(_.name), List("forward", "right"))
    assertEquals(commands.head.args, List(20.0))
  }
  test("recursive calls restore their caller's parameters and local variables") {
    val program = BeProgram.fromPythonString(
      """def branch(length, depth):
        |    if depth == 0:
        |        forward(length)
        |    else:
        |        branch(length / 2, depth - 1)
        |        branch(length / 2, depth - 1)
        |branch(80, 2)
        |forward(7)
        |""".stripMargin)
    val commands = BeExpressionToTurtleCommands(program.fullProgram)
    assertEquals(commands.filter(_.name == "forward").map(_.args),
      List(List(20.0), List(20.0), List(20.0), List(20.0), List(7.0)))
  }
  test("modulo drives alternating shapes and every-fourth branches") {
    for (period <- List(2, 4)) {
      val program = BeProgram.fromPythonString(s"""for i in range(1, 8):
        |    if i % $period == 0:
        |        forward(40)
        |    else:
        |        forward(10)
        |""".stripMargin)
      val commands = BeExpressionToTurtleCommands(program.fullProgram)
      assertEquals(commands.map(_.args.head), (1 to 7).map(i => if i % period == 0 then 40.0 else 10.0).toList)
    }
  }

  test("remainder agrees with Python for signed integer and fractional operands") {
    val program = BeProgram.fromPythonString("negative_divisor = -3\nforward(-5 % 3)\nforward(5 % negative_divisor)\nforward(5.5 % 2)\n")
    assertEquals(BeExpressionToTurtleCommands(program.fullProgram).map(_.args),
      List(List(1.0), List(-1.0), List(1.5)))
  }

}
