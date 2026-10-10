package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.geometry.{Line, Point}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{SvgToTurtleProgram, TurtleGraphic}
import munit.FunSuite

class TurtleGraphicSpec extends FunSuite {
  private def assertEqualsDouble(actual: Double, expected: Double, tolerance: Double): Unit = {
    assert(Math.abs(actual - expected) < tolerance, s"Expected $expected but got $actual (diff: ${Math.abs(actual - expected)})")
  }
  private val converter = new SvgToTurtleProgram

  test("TurtleGraphicSvgString converts to turtle program") {
    val graphic = TurtleGraphic.TurtleGraphicSvgString("M0 0 L10 0 L10 10 Z")
    val commands = graphic.toTurtleProgram
    assert(commands.nonEmpty)
    // M command moves to (0,0) without drawing, so first command is penUp (starting from default position)
  }

  test("TurtleGraphicSvgString returns original SVG string") {
    val svgString = "M0 0 L10 0 L10 10 Z"
    val graphic = TurtleGraphic.TurtleGraphicSvgString(svgString)
    assertEquals(graphic.toSvgPathDString, svgString)
  }

  test("TurtleGraphicProgram converts to turtle program") {
    val program: List[TurtleCommand[Double]] = List(TurtleCommand("penUp"), TurtleCommand("forward", List(10.0)))
    val graphic = TurtleGraphic.TurtleGraphicProgram(program)
    val commands = graphic.toTurtleProgram
    assertEquals(commands.size, 2)
    assertEquals(commands.head.name, "penUp")
    assertEquals(commands(1).name, "forward")
  }

  test("TurtleGraphicProgram converts to SVG path") {
    val program: List[TurtleCommand[Double]] = List(
      TurtleCommand("penUp"),
      TurtleCommand("forward", List(10.0)),
      TurtleCommand("penDown"),
      TurtleCommand("forward", List(10.0))
    )
    val graphic = TurtleGraphic.TurtleGraphicProgram(program)
    val svg = graphic.toSvgPathDString
    assert(svg.contains("M") || svg.contains("L"))
  }

  test("TurtleLineBasedProgram converts to turtle program") {
    val line = Line(Point(0.0, 0.0), Point(10.0, 0.0))
    val graphic = TurtleGraphic.TurtleLineBasedProgram(List(line))
    val commands = graphic.toTurtleProgram
    
    // Should have penUp to start, then forward
    assert(commands.nonEmpty)
    val penCommands = commands.filter(_.name == "penUp")
    assert(penCommands.nonEmpty)
  }

  test("TurtleLineBasedProgram draws multiple lines") {
    val lines = List(
      Line(Point(0.0, 0.0), Point(10.0, 0.0)),
      Line(Point(10.0, 0.0), Point(10.0, 10.0))
    )
    val graphic = TurtleGraphic.TurtleLineBasedProgram(lines)
    val commands = graphic.toTurtleProgram
    
    val forwards = commands.filter(_.name == "forward")
    assert(forwards.size >= 2)
  }

  test("TurtleLineBasedProgram converts to SVG path") {
    val line = Line(Point(0.0, 0.0), Point(10.0, 10.0))
    val graphic = TurtleGraphic.TurtleLineBasedProgram(List(line))
    val svg = graphic.toSvgPathDString
    assert(svg.contains("M"))
    assert(svg.contains("L"))
  }

  test("TurtleLineBasedProgram handles empty list") {
    val graphic = TurtleGraphic.TurtleLineBasedProgram(List())
    val svg = graphic.toSvgPathDString
    assertEquals(svg, "")
  }

  test("roundtrip: SVG string -> turtle program -> TurtleGraphicSvgString") {
    val originalSvg = "M0 0 L10 0 L10 10 Z"
    val originalCommands = converter.transform(originalSvg)
    val graphic = TurtleGraphic.TurtleGraphicSvgString(originalSvg)
    val convertedCommands = graphic.toTurtleProgram
    
    assertEquals(convertedCommands.size, originalCommands.size)
  }

  test("roundtrip: turtle program -> TurtleGraphicProgram -> back to commands") {
    val originalCommands = converter.transform("M0 0 L10 10")
    val graphic = TurtleGraphic.TurtleGraphicProgram(originalCommands)
    val convertedCommands = graphic.toTurtleProgram
    
    assertEquals(convertedCommands.size, originalCommands.size)
  }

  test("TurtleLineBasedProgram handles pen state correctly") {
    val lines = List(
      Line(Point(0.0, 0.0), Point(10.0, 0.0)),
      Line(Point(20.0, 0.0), Point(30.0, 0.0)) // Gap, needs jump
    )
    val graphic = TurtleGraphic.TurtleLineBasedProgram(lines)
    val commands = graphic.toTurtleProgram
    
    val penUps = commands.filter(_.name == "penUp")
    val penDowns = commands.filter(_.name == "penDown")
    
    // Should have penUp before the gap and penDown after
    assert(penUps.nonEmpty)
    assert(penDowns.nonEmpty)
  }

  test("TurtleLineBasedProgram uses shortest turns (no rotation > 180°)") {
    val lines = List(
      Line(Point(0.0, 0.0), Point(10.0, 0.0)),
      Line(Point(10.0, 0.0), Point(0.0, 1.0)) // Sharp turn back
    )
    val graphic = TurtleGraphic.TurtleLineBasedProgram(lines)
    val commands = graphic.toTurtleProgram
    
    val turns = commands.filter(c => c.name == "turnLeft" || c.name == "turnRight")
      .flatMap(_.args)
    
    assert(turns.forall(angle => angle >= 0 && angle <= 180))
  }

  test("TurtleLineBasedProgram draws closed triangle") {
    val lines = List(
      Line(Point(0.0, 0.0), Point(10.0, 0.0)),
      Line(Point(10.0, 0.0), Point(5.0, 8.66)),
      Line(Point(5.0, 8.66), Point(0.0, 0.0))
    )
    val graphic = TurtleGraphic.TurtleLineBasedProgram(lines)
    val commands = graphic.toTurtleProgram
    
    val forwards = commands.filter(_.name == "forward")
    assertEquals(forwards.size, 3)
  }
}
