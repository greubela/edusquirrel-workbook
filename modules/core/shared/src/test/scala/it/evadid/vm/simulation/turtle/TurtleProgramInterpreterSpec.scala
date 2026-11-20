package it.evadid.vm.simulation.turtle

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.controlStructures.{BeIfElse, BeSequence, BeWhile}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import munit.FunSuite

class TurtleProgramInterpreterSpec extends FunSuite {
  private def run(code: String, limits: TurtleExecutionLimits = TurtleExecutionLimits()) =
    TurtleProgramAnalysis(ProgrammingStateRaw(Python, code).toBeExpression.expression, limits)
  private def distances(code: String): List[Double] = run(code).completedCommands.map(_.args.head)

  test("comparison and boolean operators decide branches like Python") {
    val conditions = Seq(
      "3 > 2" -> true, "2 > 3" -> false, "2 <= 2" -> true, "3 <= 2" -> false, "2 >= 3" -> false, "3 >= 3" -> true,
      "1 != 2" -> true, "2 != 2" -> false, "1 == 1" -> true, "1 < 2" -> true,
      "True and False" -> false, "True and True" -> true, "False or True" -> true, "False or False" -> false,
      "not False" -> true, "not True" -> false)
    conditions.foreach { (condition, expected) =>
      assertEquals(distances(s"if $condition:\n    forward(1)\nelse:\n    forward(2)"), List(if expected then 1.0 else 2.0), condition)
    }
  }

  test("arithmetic follows Python semantics for unary minus, division and floor modulo") {
    assertEquals(distances("x = 7\nforward(x % 3)\nforward(-x % 3)\nforward(-x)\nforward(x / 2)\nforward(10 - 2 - 3)\nforward(2 * 3 * 4)"),
      List(1.0, 2.0, -7.0, 3.5, 5.0, 24.0))
  }

  test("division or remainder by zero fails the run but keeps the commands drawn before") {
    val divided = run("forward(1)\nforward(1 / 0)\nforward(2)")
    assertEquals(divided.executedTurtleCommandsInOrder, List(TurtleCommand.Forward(1.0)))
    assertEquals(divided.executionStatus, TurtleExecutionStatus.Failed("Division by zero."))
    assertEquals(run("forward(5 % 0)").executionStatus, TurtleExecutionStatus.Failed("Remainder by zero."))
  }

  test("counting loops bind their variable and unknown names default to zero") {
    assertEquals(distances("for i in range(2, 5):\n    forward(i)"), List(2.0, 3.0, 4.0))
    assertEquals(distances("forward(unknown + 4)"), List(4.0))
  }

  test("while loops, recursion and command counts stop at their limits") {
    val endless = run("x = 0\nwhile x < 1:\n    forward(1)", TurtleExecutionLimits(maxLoopIterations = 3))
    assertEquals(endless.executionStatus, TurtleExecutionStatus.LimitExceeded)
    assertEquals(endless.executedTurtleCommandsInOrder.size, 3)
    val recursive = run("def spiral(n):\n    forward(n)\n    spiral(n + 1)\nspiral(1)", TurtleExecutionLimits(maxCallDepth = 4))
    assertEquals(recursive.executedTurtleCommandsInOrder.map(_.args.head), List(1.0, 2.0, 3.0, 4.0))
    assertEquals(recursive.executionStatus, TurtleExecutionStatus.LimitExceeded)
    val many = run("for _ in range(10):\n    forward(1)", TurtleExecutionLimits(maxCommands = 4))
    assertEquals((many.executedTurtleCommandsInOrder.size, many.executionStatus), (4, TurtleExecutionStatus.LimitExceeded))
    val steps = run("for _ in range(10):\n    forward(1)", TurtleExecutionLimits(maxSteps = 5))
    assertEquals(steps.executionStatus, TurtleExecutionStatus.LimitExceeded)
    val cancelled = TurtleProgramAnalysis(ProgrammingStateRaw(Python, "forward(1)").toBeExpression.expression, isCancelled = () => true)
    assertEquals((cancelled.executedTurtleCommandsInOrder, cancelled.executionStatus), (Nil, TurtleExecutionStatus.Cancelled))
  }

  test("empty programs, empty conditions and non-finite arguments are handled explicitly") {
    assertEquals(TurtleProgramAnalysis(BeStartProgram(None)).completedCommands, Nil)
    val emptyIf = BeIfElse(BeSequence.conditionalBody(Nil), BeSequence.optionalBody(Nil), BeSequence.optionalBody(Nil))
    assertEquals(TurtleProgramAnalysis(emptyIf).executionStatus, TurtleExecutionStatus.Completed)
    val emptyWhile = BeWhile(BeSequence.conditionalBody(Nil), BeSequence.optionalBody(Nil))
    assertEquals(TurtleProgramAnalysis(emptyWhile).executionStatus, TurtleExecutionStatus.Completed)
    assertEquals(run("forward(1e308 * 10)").executionStatus, TurtleExecutionStatus.Failed("A drawing command needs finite numbers."))
    val path = TurtleProgramAnalysis.toPathBuilder(ProgrammingStateRaw(Python, "forward(10)").toBeExpression.expression)
    assertEquals(path.turtleCommands, List(TurtleCommand.Forward(10.0)))
  }

  test("object operations are rejected by the drawing simulator") {
    val code = "class Box:\n    def __init__(self):\n        self.size = 1\nbox = Box()\nforward(box.size)"
    run(code).executionStatus match {
      case TurtleExecutionStatus.Failed(message) => assert(message.contains("object operations"), message)
      case other => fail(s"Unexpected $other")
    }
  }
}
