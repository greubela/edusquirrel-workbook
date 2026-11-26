package it.evadid.vm.simulation.java

import it.evadid.vm.compilation.java.turtle.JavaTurtleCompiler
import it.evadid.vm.conversion.expressionpython.JavaTurtlePythonExport
import munit.FunSuite

class JavaTurtleArithmeticSpec extends FunSuite {
  private def program(body: String) =
    JavaTurtleCompiler.compile(s"class Main { public static void main(String[] args) { $body } }").fold(problem => fail(problem.message), identity)
  private def drawn(body: String): Vector[Double] = {
    val execution = JavaTurtleInterpreter.runVm(program(body))
    assertEquals(execution.status, JavaTurtleInterpreter.Status.Completed, body)
    execution.commands.map(_.value)
  }

  test("double arithmetic, mixed int and double operands and comparisons follow Java") {
    assertEquals(drawn("double a = 1.5; Turtle.forward(a * 2); Turtle.forward(2 * a); Turtle.forward(+a); Turtle.forward(-a);"),
      Vector(3.0, 3.0, 1.5, -1.5))
    assertEquals(drawn("double a = 2.5; int b = 2; Turtle.forward(b + a); Turtle.forward(a - b); Turtle.forward(a % b); Turtle.forward(7 / b);"),
      Vector(4.5, 0.5, 0.5, 3.0))
    assertEquals(drawn("double a = 2.5; if (a < 3.0) { Turtle.forward(1); } if (a >= 2.5) { Turtle.forward(2); } if (a <= 1.0) { Turtle.forward(3); } if (a > 1) { Turtle.forward(4); }"),
      Vector(1.0, 2.0, 4.0))
    assertEquals(drawn("int i = 2147483647; i++; Turtle.forward(i);"), Vector(Int.MinValue.toDouble))
    assertEquals(drawn("double d = 0.5; d++; Turtle.forward(d); d--; d--; Turtle.forward(d);"), Vector(1.5, -0.5))
  }

  test("the Python export reproduces integer overflow, double operators and updates") {
    val exported = JavaTurtlePythonExport.render(program(
      "int i = 3; double d = 1.5; boolean b = true; i = i * 2 - 1 + i % 2; d = d * 2 - 1 + d % 2; i++; --d; if (b) { Turtle.forward(i + d); }")).source
    Seq("_int32(", "_remainder(", "_double_remainder(", "float(", "True").foreach(part => assert(exported.contains(part), s"'$part' missing in\n$exported"))
    assertEquals(JavaTurtlePythonExport.doubleLiteral(Double.NaN), "float(\"nan\")")
    assertEquals(JavaTurtlePythonExport.doubleLiteral(Double.PositiveInfinity), "float(\"inf\")")
    assertEquals(JavaTurtlePythonExport.doubleLiteral(Double.NegativeInfinity), "float(\"-inf\")")
    assertEquals(JavaTurtlePythonExport.doubleLiteral(2.0), "2.0")
    assertEquals(JavaTurtlePythonExport.doubleLiteral(1e300).toDouble, 1e300)
  }
  test("left turns and pen commands retain their typed drawing commands and report values") {
    val compiled = program("Turtle.penUp(); Turtle.turnLeft(90); Turtle.forward(20); Turtle.turnRight(90); Turtle.penDown();")
    val execution = JavaTurtleInterpreter.runVm(compiled)
    assertEquals(execution.status, JavaTurtleInterpreter.Status.Completed)
    import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
    assertEquals(execution.commands.map(_.toDrawingCommand), Vector[TurtleCommand[Double]](
      TurtleCommand.PenUp, TurtleCommand.Left(90), TurtleCommand.Forward(20), TurtleCommand.Right(90), TurtleCommand.PenDown))
    val exported = JavaTurtlePythonExport.render(compiled).source
    assert(exported.contains("_command(\"penup\", 0)"))
    assert(exported.contains("_command(\"pendown\", 0)"))
    assert(exported.contains("_command(\"left\","))
  }

  test("pen commands reject arguments and turns require a numeric argument") {
    for call <- List("Turtle.penUp(1);", "Turtle.penDown(0);", "Turtle.turnLeft();", "Turtle.turnLeft(true);") do
      assert(JavaTurtleCompiler.compile(s"class Main { public static void main(String[] args) { $call } }").isLeft, call)
  }

}
