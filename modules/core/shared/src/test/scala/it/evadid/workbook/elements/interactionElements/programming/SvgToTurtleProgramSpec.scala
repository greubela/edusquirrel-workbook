package it.evadid.workbook.elements.interactionElements.programming
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.SvgToTurtleProgram
import munit.FunSuite

class SvgToTurtleProgramSpec extends FunSuite {
  private val converter = new SvgToTurtleProgram

  test("converts absolute, relative, horizontal, vertical and close commands") {
    val commands = converter.transform("M 10 10 h 20 v 15 l -20 0 z")
    val path = TurtlePathBuilder(Point(0.0, 0.0), commands)
    assertEqualsDouble(path.turtleState.x, 10.0, 1e-8)
    assertEqualsDouble(path.turtleState.y, 10.0, 1e-8)
    assertEquals(commands.map(_.name).toSet -- Set("forward", "turnLeft", "turnRight", "penUp", "penDown"), Set.empty)
  }

  test("uses shortest turns, never exceeding 180 degrees") {
    val commands = converter("M0 0 L-10 -1 L0 0")
    val turns = commands.filter(c => c.name == "turnLeft" || c.name == "turnRight").flatMap(_.args)
    assert(turns.nonEmpty)
    assert(turns.forall(angle => angle >= 0 && angle <= 180), clues(turns))
  }

  test("turns bezier curves into lines through all intermediate control points") {
    val commands = converter("M0,0 C10,0 10,10 20,10 Q30,10 30,20 T40,30")
    val forwards = commands.filter(_.name == "forward")
    assertEquals(forwards.size, 7)
    val path = TurtlePathBuilder(Point(0.0, 0.0), commands)
    assertEqualsDouble(path.turtleState.x, 40.0, 1e-8)
    assertEqualsDouble(path.turtleState.y, 30.0, 1e-8)
  }

  test("approximates elliptical arcs with intermediate line segments") {
    val commands = converter("M0 0 A10 10 0 0 1 20 0")
    assert(commands.count(_.name == "forward") > 2)
    val path = TurtlePathBuilder(Point(0.0, 0.0), commands)
    assertEqualsDouble(path.turtleState.x, 20.0, 1e-7)
    assertEqualsDouble(path.turtleState.y, 0.0, 1e-7)
  }

  test("supports compact syntax, exponents, repeated arguments and multiple subpaths") {
    val commands = converter("m1e1-5 5 0 0 5m10,10l5,0")
    assertEquals(commands.count(_.name == "penUp"), 2)
    assertEquals(commands.count(_.name == "penDown"), 2)
    val path = TurtlePathBuilder(Point(0.0, 0.0), commands)
    assertEqualsDouble(path.turtleState.x, 30.0, 1e-8)
    assertEqualsDouble(path.turtleState.y, 10.0, 1e-8)
  }
}
