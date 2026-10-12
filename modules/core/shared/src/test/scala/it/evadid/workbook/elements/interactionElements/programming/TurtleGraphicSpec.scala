package it.evadid.workbook.elements.interactionElements.programming
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*

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

  private def cmd(name: String, args: Double*): TurtleCommand[Double] = TurtleCommand(name, args.toList)
  private def trace(commands: TurtleCommand[Double]*): TurtleGraphic = TurtleGraphic.TurtleGraphicProgram(commands.toList)
  private def point(actual: Point[Double], x: Double, y: Double): Unit = {
    assertEqualsDouble(actual.x, x, 1e-7)
    assertEqualsDouble(actual.y, y, 1e-7)
  }

  test("drawn lines exclude travel; movements retain pen state, order and coordinates") {
    val graphic = trace(cmd("forward", 10), cmd("pen_up"), cmd("goto", 20, 5), cmd("pen_down"), cmd("setx", 30))
    assertEquals(graphic.renderMovements.map(_.jump).toList, List(false, true, false))
    assertEquals(graphic.renderLines.size, 2)
    point(graphic.renderMovements(1).start, 10, 0)
    point(graphic.renderMovements(1).end, 20, 5)
    point(graphic.renderLines.last.end, 30, 5)
  }

  test("signed turns accumulate across zero forward; cancelled and full turns have no angle") {
    val graphic = trace(cmd("forward", 10), cmd("left", 120), cmd("forward", 0), cmd("right", 30), cmd("forward", 20))
    assertEquals(graphic.renderMovements.size, 2)
    val angle = graphic.renderAngles.head
    assertEquals((angle.lineBefore, angle.lineAfter), (0, 1))
    assertEquals(angle.degrees, 90.0)
    assertEquals(angle.fromHeading, 0.0)
    point(angle.vertex, 10, 0)
    point(graphic.renderLines.last.end, 10, -20)
    for turns <- List(List(cmd("left", 90), cmd("right", 90)), List(cmd("left", 360))) do {
      val cancelled = TurtleGraphic.TurtleGraphicProgram(List(cmd("forward", 10)) ++ turns ++ List(cmd("forward", 10)))
      assertEquals(cancelled.renderAngles.toList, Nil)
    }
  }

  test("aliases, backwards and negative forwards follow SVG coordinates") {
    val graphic = trace(cmd("bk", 10), cmd(" TURN-RIGHT ", 90), cmd("pu"), cmd("fd", 20), cmd("pd"), cmd("lt", 90), cmd("fd", -5))
    graphic.renderMovements.zip(List((-10.0, 0.0), (-10.0, 20.0), (-15.0, 20.0))).foreach { (movement, xy) => point(movement.end, xy._1, xy._2) }
    assertEquals(graphic.renderAngles.map(_.degrees).toList, List(-90.0, 90.0))
  }

  test("absolute moves, including no-op goto, and setheading break pending turn annotations") {
    for command <- List(cmd("goto", 20, 30), cmd("goto", 10, 0), cmd("setx", 20), cmd("sety", 30), cmd("setheading", 90), cmd("home")) do {
      assertEquals(trace(cmd("forward", 10), cmd("left", 45), command, cmd("forward", 5)).renderAngles.toList, Nil)
    }
  }

  test("clear retains position and pen state; reset restores defaults and angle indices") {
    val prefix = List(cmd("forward", 10), cmd("right", 90), cmd("penup"))
    val cleared = TurtleGraphic.TurtleGraphicProgram(prefix ++ List(cmd("clear"), cmd("forward", 5)))
    assertEquals(cleared.renderMovements.size, 1)
    assert(cleared.renderMovements.head.jump)
    point(cleared.renderMovements.head.start, 10, 0)
    point(cleared.renderMovements.head.end, 10, 5)
    val reset = TurtleGraphic.TurtleGraphicProgram(prefix ++ List(cmd("reset"), cmd("forward", 5), cmd("left", 90), cmd("forward", 2)))
    assert(!reset.renderMovements.head.jump)
    point(reset.renderMovements.head.start, 0, 0)
    point(reset.renderMovements.head.end, 5, 0)
    assertEquals(reset.renderAngles.map(a => (a.lineBefore, a.lineAfter)).toList, List((0, 1)))
    assertEquals(cleared.renderAngles.toList, Nil)
  }

  test("empty, zero-length, incomplete and unknown commands do not invent geometry") {
    val graphic = trace(cmd("forward", 0), cmd("goto", 0, 0), cmd("forward"), cmd("goto", 3), cmd("unknown", 5))
    assertEquals(graphic.renderMovements.toList, Nil)
    assertEquals(graphic.renderLines.toList, Nil)
    assertEquals(graphic.renderAngles.toList, Nil)
  }

  test("line-based SVG preserves disconnected strokes and round-trips their geometry") {
    val lines = List(Line(Point(0.0, 0.0), Point(10.0, 0.0)), Line(Point(20.0, 5.0), Point(30.0, 5.0)))
    val graphic = TurtleGraphic.TurtleLineBasedProgram(lines)
    assertEquals(graphic.toSvgPathDString, "M 0 0 L 10 0 M 20 5 L 30 5")
    for variant <- List(graphic, TurtleGraphic.TurtleGraphicSvgString(graphic.toSvgPathDString)) do {
      assertEquals(variant.renderLines.size, 2)
      variant.renderLines.zip(lines).foreach { (actual, expected) =>
        point(actual.start, expected.start.x, expected.start.y)
        point(actual.end, expected.end.x, expected.end.y)
      }
    }
    val closed = TurtleGraphic.TurtleGraphicSvgString("M 0 0 L 10 0 L 10 10 Z")
    assertEquals(closed.renderLines.size, 3)
    point(closed.renderLines.last.end, 0, 0)
  }

  test("circle and arc traces agree with the existing SVG builder approximation") {
    for command <- List(cmd("circle", 10), cmd("circle", -10, 90), cmd("arc_left", 10, 120), cmd("arc_right", 10, 120)) do {
      val graphic = trace(command)
      val fromSvg = TurtleGraphic.TurtleGraphicSvgString(graphic.toSvgPathDString)
      assert(graphic.renderLines.nonEmpty)
      assertEquals(graphic.renderLines.size, fromSvg.renderLines.size)
      graphic.renderLines.zip(fromSvg.renderLines).foreach { (line, svgLine) =>
        point(line.start, svgLine.start.x, svgLine.start.y)
        point(line.end, svgLine.end.x, svgLine.end.y)
      }
    }
  }

  test("nonfinite arguments and coordinate overflow are rejected") {
    for invalid <- List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity) do
      intercept[IllegalArgumentException](trace(cmd("forward", invalid)).renderLines)
    intercept[IllegalArgumentException](trace(cmd("goto", Double.MaxValue, 0), cmd("forward", Double.MaxValue)).renderLines)
  }

  test("dot draws its SVG outline with the pen up and preserves turtle position and heading") {
    val graphic = trace(cmd("goto", 10, 20), cmd("setheading", 90), cmd("penup"), cmd("dot", 4), cmd("forward", 5))
    assert(graphic.renderLines.size > 1)
    val travel = graphic.renderMovements.last
    assert(travel.jump)
    point(travel.start, 10, 20)
    point(travel.end, 10, 15)
    val fromSvg = TurtleGraphic.TurtleGraphicSvgString(graphic.toSvgPathDString)
    assertEquals(graphic.renderLines.size, fromSvg.renderLines.size)
    graphic.renderLines.zip(fromSvg.renderLines).foreach { (line, svgLine) =>
      point(line.start, svgLine.start.x, svgLine.start.y)
      point(line.end, svgLine.end.x, svgLine.end.y)
    }
  }
}
