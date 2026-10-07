package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import munit.FunSuite
import upickle.default.*

class TurtleCommandSerializationSpec extends FunSuite:
  test("numeric commands retain their existing serialized format") {
    val command = TurtleCommand[Double]("forward", List(12.5))
    assertEquals(write(command), "[\"forward\",[12.5]]")
    assertEquals(read[TurtleCommand[Double]](write(command)), command)
  }

  test("existing saved commands without textual arguments still decode") {
    assertEquals(read[TurtleCommand[Double]]("[\"penup\",[]]"), TurtleCommand[Double]("penup"))
    assertEquals(read[TurtleCommand[Double]]("[\"right\",[90]]"), TurtleCommand[Double]("right", List(90.0)))
  }

  test("textual and mixed arguments survive serialization") {
    val commands = List(
      TurtleCommand[Double]("color", stringArgs = List("#12abef")),
      TurtleCommand[Double]("jump_stitch", stringArgs = List("true")),
      TurtleCommand[Double]("custom", List(1.5, 2.0), List("label", "false"))
    )
    assertEquals(read[List[TurtleCommand[Double]]](write(commands)), commands)
  }

  test("RGB color arguments remain numeric rather than becoming a text color") {
    val builder = TurtlePathBuilder[Double]().handleStringCommand(TurtleCommand("color", List(12.0, 34.0, 56.0)))
    assertEquals(builder.turtleState.penColor, "rgb(12,34,56)")
  }

  test("saved commands reject malformed tuple arities") {
    List("[]", "[\"forward\"]", "[\"forward\",[10],[],[]]").foreach { serialized =>
      intercept[Exception](read[TurtleCommand[Double]](serialized))
    }
  }

  test("saved commands reject invalid field types") {
    List(
      "[1,[]]",
      "[\"forward\",{}]",
      "[\"forward\",[{}]]",
      "[\"color\",[],{}]",
      "[\"color\",[],[{}]]"
    ).foreach { serialized =>
      intercept[Exception](read[TurtleCommand[Double]](serialized))
    }
  }
