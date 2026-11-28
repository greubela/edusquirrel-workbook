package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder
import it.evadid.workbook.interactions.programming.state.*
import munit.FunSuite

/** The Java `Turtle` class that printed programs need on a plain JVM draws what the shared turtle
  * simulation, which grades and previews answers, draws for the same program.
  */
class JavaTurtleLibrarySpec extends FunSuite {
  private def javaSegments(python: String): Vector[Vector[Double]] = {
    val code = ProgrammingStateRaw(Python, python).convertToLanguage(Java).finalState
      .getOrElse(fail(s"No Java for:\n$python")).asInstanceOf[ProgrammingStateJavaStructured].toString
    JavaTestRuntime.compileAndRun(code)(JavaTestRuntime.turtleSegments)
  }

  /** Pen-down movements of the shared simulation, one segment per command, with Python's initial heading 0. */
  private def simulatedSegments(python: String): Vector[Vector[Double]] = {
    segmentsOf(ProgrammingStateRaw(Python, python).toBeExpression.turtleCommandExecutionInfo.completedCommands)
  }
  /** The simulation's arcs as their individual chords: an arc command is replayed step by step. */
  private def simulatedArcSegments(python: String): Vector[Vector[Double]] = {
    import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.*
    val commands = ProgrammingStateRaw(Python, python).toBeExpression.turtleCommandExecutionInfo.completedCommands.flatMap {
      case arc @ (_: ArcLeft[Double] | _: ArcRight[Double] | _: Circle[Double]) =>
        val (radius, extent, left) = arc match {
          case ArcLeft(radius, extent) => (radius, extent, true)
          case ArcRight(radius, extent) => (radius, extent, false)
          case Circle(radius, extent, _) => (radius, extent.getOrElse(360.0), true)
          case _ => throw IllegalStateException()
        }
        val (steps, length, turn) = TurtlePathBuilder.arcSteps(radius, extent, left)
        (Left(turn / 2) +: List.fill(steps)(List(Forward(length), Left(turn))).flatten) :+ Left(-turn / 2)
      case other => List(other)
    }
    segmentsOf(commands)
  }
  private def segmentsOf(commands: List[it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand[Double]]): Vector[Vector[Double]] =
    commands.foldLeft((TurtlePathBuilder[Double](), Vector.empty[Vector[Double]])) { case ((builder, segments), command) =>
      val next = builder.handleCommand(command)
      val moved = (next.getX(), next.getY()) != (builder.getX(), builder.getY())
      val kept = if command.isInstanceOf[it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.Clear.type] then Vector.empty else segments
      (next, if moved && builder.turtleState.penDown then kept :+ Vector(builder.getX(), builder.getY(), next.getX(), next.getY()) else kept)
    }._2

  private def rounded(segments: Vector[Vector[Double]]) = segments.map(_.map(value => math.round(value * 1e6) / 1e6 + 0.0))

  private val programs = List(
    "forward and turns" -> "forward(30)\nleft(90)\nforward(20)\nright(45)\nforward(10)\n",
    "backward" -> "forward(10)\nbackward(25)\nleft(180)\nback(5)\n",
    "absolute moves" -> "goto(10, 20)\nsetx(-5)\nsety(7)\nsetheading(90)\nforward(3)\n",
    "home" -> "goto(40, 40)\nleft(30)\nhome()\nforward(10)\n",
    "pen up and down" -> "forward(10)\npenup()\nforward(10)\npendown()\nforward(10)\npen_up()\nleft(90)\nforward(5)\npen_down()\nforward(5)\n",
    "loops and functions" -> "def side(length):\n    forward(length)\n    left(120)\n\nfor i in range(3):\n    side(10 + i)\n",
    "functions returning values" -> "def double(x: float) -> float:\n    return x * 2\n\ndef big(n: float) -> bool:\n    return n > 10\n\nfor i in range(3):\n    if big(double(i * 4)):\n        forward(double(i))\n    else:\n        left(90)\n",
    "clear keeps the position" -> "forward(15)\nclear()\nforward(5)\n")

  programs.foreach { (label, python) =>
    test(s"$label: the Java turtle draws the segments of the shared simulation") {
      assertEquals(rounded(javaSegments(python)), rounded(simulatedSegments(python)), python)
    }
  }

  test("circles and arcs follow Python's turtle.circle in both: the same chords, ending exactly on the circle") {
    List("circle(20)\nforward(5)\n", "arc_left(30, 90)\nforward(10)\n", "arc_right(15, 180)\nforward(10)\n",
        "circle(-10)\nforward(5)\n").foreach { python =>
      assertEquals(rounded(javaSegments(python)), rounded(simulatedArcSegments(python)), python)
    }
    val quarter = javaSegments("arc_left(30, 90)\n")
    assertEquals(quarter.size, 1 + ((11 + 30 / 6.0) * 0.25).toInt, "CPython's step count")
    assertEqualsDouble(quarter.last(2), 30.0, 1e-9)
    assertEqualsDouble(quarter.last(3), 30.0, 1e-9)
    val circle = javaSegments("circle(20)\n")
    assertEqualsDouble(circle.last(2), 0.0, 1e-9)
    assertEqualsDouble(circle.last(3), 0.0, 1e-9)
  }

  test("the library declares exactly the Java names the printer calls") {
    val source = it.evadid.vm.conversion.dependencies.JavaTurtleLibrary.source
    it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.turtleClass.methods.foreach { method =>
      val name = it.evadid.vm.conversion.dependencies.JavaTurtleLibrary.methodName(method)
      assert(source.contains(s"public static void $name("), name)
    }
    assert(!source.contains("public static void left(") && !source.contains("public static void goto("),
      "Java uses turnLeft and setPosition; goto is reserved")
  }
}
