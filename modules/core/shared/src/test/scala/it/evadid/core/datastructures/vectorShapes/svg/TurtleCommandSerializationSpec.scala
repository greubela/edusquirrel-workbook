package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import munit.FunSuite
import upickle.default.*

class TurtleCommandSerializationSpec extends FunSuite:
  test("numeric commands use tagged variants with named fields") {
    val command = TurtleCommand.Forward[Double](12.5)
    assertEquals(writeJs[TurtleCommand[Double]](command), ujson.Obj("$type" -> "Forward", "distance" -> 12.5))
    assertEquals(read[TurtleCommand[Double]](write(command)), command)
  }

  test("zero-argument commands are singletons written by name; numeric variants decode with the current schema") {
    assertEquals(writeJs[TurtleCommand[Double]](TurtleCommand.PenUp), ujson.Str("PenUp"))
    assertEquals(read[TurtleCommand[Double]]("\"PenUp\""), TurtleCommand.PenUp)
    assertEquals(read[TurtleCommand[Double]]("{\"$type\":\"Right\",\"degrees\":90}"), TurtleCommand.Right[Double](90.0))
  }

  test("RGB boundary arguments retain their rendered color") {
    val builder = TurtlePathBuilder[Double]().handleCommand(TurtleCommand.fromText("color", List(12.0, 34.0, 56.0)))
    assertEquals(builder.turtleState.penColor, "rgb(12,34,56)")
  }

  test("saved commands reject old tuples and missing or unknown variants") {
    List("[]", "[\"forward\",[10]]", "{}", "{\"$type\":\"Forward\"}", "{\"$type\":\"Unknown\"}").foreach { serialized =>
      intercept[Exception](read[TurtleCommand[Double]](serialized))
    }
  }

  test("saved commands reject invalid named-field types") {
    List("{\"$type\":\"Forward\",\"distance\":{}}", "{\"$type\":\"Color\",\"color\":{}}",
      "{\"$type\":\"Goto\",\"x\":1,\"y\":[]}").foreach { serialized =>
      intercept[Exception](read[TurtleCommand[Double]](serialized))
    }
  }
