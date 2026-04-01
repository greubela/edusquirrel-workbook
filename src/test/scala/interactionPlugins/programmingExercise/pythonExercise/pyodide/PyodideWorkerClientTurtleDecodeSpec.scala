package interactionPlugins.programmingExercise.pythonExercise.pyodide

import munit.FunSuite
import scala.scalajs.js
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.CallbackOp

class PyodideWorkerClientTurtleDecodeSpec extends FunSuite {

  test("readNumberField reports missing field name") {
    val dyn = js.Dynamic.literal()

    val ex = intercept[IllegalArgumentException] {
      PyodideWorkerClient.readNumberField[Double](dyn, "x")
    }

    assert(ex.getMessage.contains("Missing required field 'x'"))
  }

  test("readBoolField reports wrong type with field name") {
    val dyn = js.Dynamic.literal(penDown = "yes")

    val ex = intercept[IllegalArgumentException] {
      PyodideWorkerClient.readBoolField(dyn, "penDown")
    }

    assert(ex.getMessage.contains("Expected boolean field 'penDown'"))
  }

  test("parsePoint reports missing nested field name") {
    val dyn = js.Dynamic.literal(y = 4)

    val ex = intercept[IllegalArgumentException] {
      PyodideWorkerClient.parsePoint[Double](dyn, "startPoint")
    }

    assert(ex.getMessage.contains("startPoint.x"))
  }

  test("parseTurtleState reports wrong numeric type with field name") {
    val dyn = js.Dynamic.literal(
      x = "not-a-number",
      y = 0,
      headingDeg = 90,
      penDown = true,
      visible = true
    )

    val ex = intercept[IllegalArgumentException] {
      PyodideWorkerClient.parseTurtleState[Double](dyn)
    }

    assert(ex.getMessage.contains("Expected numeric field 'x'"))
  }

  test("turtleBuilderFromCallbacks replays turtle operations in order") {
    val callbackOps = Seq(
      CallbackOp("turtle", "forward", Vector(50)),
      CallbackOp("turtle", "left", Vector(90)),
      CallbackOp("turtle", "forward", Vector(25))
    )

    val builder = PyodideWorkerClient.turtleBuilderFromCallbacks[Double](callbackOps)

    assertEquals(builder.turtleState.x, 50d)
    assertEquals(builder.turtleState.y, -25d)
    assertEquals(builder.turtleState.headingDeg, 90d)
    assertEquals(builder.turtleCommands.map(_.name), List("forward", "left", "forward"))
  }
}
