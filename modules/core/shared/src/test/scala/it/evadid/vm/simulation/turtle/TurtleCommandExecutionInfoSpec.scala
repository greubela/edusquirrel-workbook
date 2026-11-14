package it.evadid.vm.simulation.turtle

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.conversion.ProgrammingStateConversions.*
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStatePythonString, ProgrammingStateJavaString}
import it.evadid.vm.naming.BeEntityName
import munit.FunSuite

class TurtleCommandExecutionInfoSpec extends FunSuite {
  private def expression(code: String) = ProgrammingStatePythonString(code).toBeExpression.expression
  private def analyze(code: String, limits: TurtleExecutionLimits = TurtleExecutionLimits()) =
    TurtleProgramAnalysis(expression(code), limits)
  private def forward(distance: Double): TurtleCommand[Double] = TurtleCommand.Forward(distance)
  private def java(body: String, helpers: String = "") = ProgrammingStateJavaString(
    s"class Drawing { public static void main(String[] args) { $body } $helpers }").toBeExpression

  test("source occurrences and executed frequencies retain their distinct parameter semantics") {
    val result = analyze("for i in range(3):\n    forward(i * 10)\nforward(10)")
    assertEquals(result.executionStatus, TurtleExecutionStatus.Completed)
    assertEquals(result.executedTurtleCommandsInOrder, List(0.0, 10.0, 20.0, 10.0).map(forward))
    assertEquals(result.turtleCommandSitesInCodeOrder.size, 2)
    assertEquals(result.nrOfCommandExecuted(forward(10)), 2)
    assertEquals(result.nrOfCommandExecutedIgnoreParameter(forward(123)), 4)
    assertEquals(result.nrOfCommandInCodeIgnoreParameter(forward(123)), 2)
    assertEquals(result.nrOfLiteralCommandInCode(forward(10)), 1)
    assertEquals(result.nrOfUnresolvedCommandSitesInCode(forward(10)), 1)
    assert(result.turtleCommandSitesInCodeOrder.head.argumentExpressions.nonEmpty)
    assertEquals(result.turtleCommandSitesInCodeOrder.head.literalCommand, None)
  }

  test("both branches and never-called definitions count once in source order") {
    val result = analyze("""def unused():
      |    forward(99)
      |if True:
      |    forward(10)
      |else:
      |    forward(20)
      |forward(10)
      |""".stripMargin)
    assertEquals(result.completedCommands, List(forward(10), forward(10)))
    assertEquals(result.turtleCommandSitesInCodeOrder.flatMap(_.literalCommand),
      List(forward(99), forward(10), forward(20), forward(10)))
    val paths = result.turtleCommandSitesInCodeOrder.map(_.path)
    assertEquals(paths.distinct.size, 4, "identical commands remain separate occurrences")
    assertEquals(result.nrOfCommandInCodeIgnoreParameter(forward(0)), 4)
    assertEquals(result.nrOfLiteralCommandInCode(forward(10)), 2)
    assertEquals(result.nrOfCommandExecuted(forward(99)), 0)
  }

  test("function calls do not expand source bodies or count helpers as drawing commands") {
    val result = analyze("def draw(n):\n    forward(n)\ndraw(10)\ndraw(20)")
    assertEquals(result.completedCommands, List(forward(10), forward(20)))
    assertEquals(result.turtleCommandSitesInCodeOrder.size, 1)
    assertEquals(result.nrOfUnresolvedCommandSitesInCode(forward(0)), 1)
    assertEquals(result.nrOfLiteralCommandInCode(forward(10)), 0)
  }

  test("loop bodies with zero iterations remain visible in static analysis") {
    val result = analyze("for _ in range(0):\n    forward(10)\nwhile False:\n    forward(20)")
    assertEquals(result.completedCommands, Nil)
    assertEquals(result.turtleCommandSitesInCodeOrder.flatMap(_.literalCommand), List(forward(10), forward(20)))
  }

  test("aliases share canonical identities in source and execution") {
    val result = analyze("turn(90)\nright(45)\npenup()\nup()\npendown()")
    val right: TurtleCommand[Double] = TurtleCommand.Right(0.0)
    val penUp: TurtleCommand[Double] = TurtleCommand.PenUp()
    assertEquals(result.nrOfCommandExecutedIgnoreParameter(right), 2)
    assertEquals(result.nrOfCommandInCodeIgnoreParameter(right), 2)
    assertEquals(result.nrOfCommandInCodeIgnoreParameter(penUp), 2)
    assertEquals(result.nrOfLiteralCommandInCode(penUp), 2)
    assertEquals(result.nrOfLiteralCommandInCode(TurtleCommand.Right(90.0)), 1)
  }

  test("arc aliases and name-only queries use the typed command identity") {
    val result = analyze("arc_right(10, 90)\narc_left(10, 90)")
    val arc: TurtleCommand[Double] = TurtleCommand.ArcRight(10.0, 90.0)
    assertEquals(result.nrOfCommandExecuted(arc), 1)
    assertEquals(result.nrOfLiteralCommandInCode(arc), 1)
    assertEquals(result.nrOfCommandInCodeIgnoreParameter(arc), 1)
    assertEquals(result.nrOfCommandExecutedIgnoreParameter(BeEntityName.fromCodeString("arcright")), 1)
    assertEquals(result.nrOfCommandInCodeIgnoreParameter(BeEntityName.fromCodeString("arcright")), 1)
  }

  test("textual parameters and stitch payloads retain exact matching") {
    val result = analyze("color('red')\ncolor('blue')\ncolor('red')\ncross_stitch(8, 13, False)")
    val red: TurtleCommand[Double] = TurtleCommand.Color("red")
    assertEquals(result.nrOfCommandExecuted(red), 2)
    assertEquals(result.nrOfLiteralCommandInCode(red), 2)
    assertEquals(result.nrOfCommandExecutedIgnoreParameter(red), 3)
    val stitch = result.completedCommands.last
    assertEquals(result.nrOfLiteralCommandInCode(stitch), 1)
  }

  test("arithmetic source expressions are not silently treated as literal arguments") {
    val result = analyze("forward(5 + 5)")
    assertEquals(result.nrOfCommandExecuted(forward(10)), 1)
    assertEquals(result.nrOfLiteralCommandInCode(forward(10)), 0)
    assertEquals(result.nrOfUnresolvedCommandSitesInCode(forward(10)), 1)
  }

  test("static analysis can inspect a nonterminating program without running it") {
    val sites = TurtleProgramAnalysis.commandSites(expression("while True:\n    forward(10)"))
    assertEquals(sites.flatMap(_.literalCommand), List(forward(10)))
  }

  test("an exhausted while guard reports partial execution instead of successful completion") {
    val result = analyze("while True:\n    forward(10)", TurtleExecutionLimits(maxLoopIterations = 2))
    assertEquals(result.executionStatus, TurtleExecutionStatus.LimitExceeded)
    assertEquals(result.executedTurtleCommandsInOrder, List(forward(10), forward(10)))
    assertEquals(result.nrOfCommandExecuted(forward(10)), 2)
    assertEquals(result.nrOfLiteralCommandInCode(forward(10)), 1)
    intercept[IllegalArgumentException](result.completedCommands)
  }

  test("a finite while loop completing exactly at the guard remains completed") {
    val result = analyze("n = 0\nwhile n < 2:\n    forward(10)\n    n = n + 1", TurtleExecutionLimits(maxLoopIterations = 2))
    assertEquals(result.executionStatus, TurtleExecutionStatus.Completed)
    assertEquals(result.completedCommands, List(forward(10), forward(10)))
  }

  test("command limits preserve partial commands and do not reject exact completion") {
    val limited = analyze("forward(1)\nforward(2)", TurtleExecutionLimits(maxCommands = 1))
    assertEquals(limited.executionStatus, TurtleExecutionStatus.LimitExceeded)
    assertEquals(limited.executedTurtleCommandsInOrder, List(forward(1)))
    assertEquals(limited.turtleCommandSitesInCodeOrder.size, 2)
    assertEquals(analyze("forward(1)", TurtleExecutionLimits(maxCommands = 1)).completedCommands, List(forward(1)))
    assertEquals(analyze("forward(1)", TurtleExecutionLimits(maxCommands = 0)).executionStatus, TurtleExecutionStatus.LimitExceeded)
  }

  test("large repeats and empty recursive calls terminate at execution limits") {
    val repeat = analyze("for _ in range(2147483647):\n    forward(1)", TurtleExecutionLimits(maxSteps = 10))
    assertEquals(repeat.executionStatus, TurtleExecutionStatus.LimitExceeded)
    val recursive = analyze("def recurse():\n    recurse()\nrecurse()", TurtleExecutionLimits(maxCallDepth = 4))
    assertEquals(recursive.executionStatus, TurtleExecutionStatus.LimitExceeded)
    assertEquals(recursive.executedTurtleCommandsInOrder, Nil)
    assertEquals(recursive.turtleCommandSitesInCodeOrder, Nil)
  }

  test("cancelled runs still expose their source occurrences") {
    val result = TurtleProgramAnalysis(expression("forward(10)"), isCancelled = () => true)
    assertEquals(result.executionStatus, TurtleExecutionStatus.Cancelled)
    assertEquals(result.executedTurtleCommandsInOrder, Nil)
    assertEquals(result.nrOfLiteralCommandInCode(forward(10)), 1)
    intercept[IllegalArgumentException](result.completedCommands)
  }

  test("nonfinite drawing parameters fail without erasing earlier commands") {
    val result = analyze("forward(1)\nforward(1e309)")
    assert(result.executionStatus.isInstanceOf[TurtleExecutionStatus.Failed])
    assertEquals(result.executedTurtleCommandsInOrder, List(forward(1)))
    intercept[IllegalArgumentException](result.completedCommands)
  }

  test("checked Java uses the same source and execution count API") {
    val result = java("draw(10); draw(20); Turtle.forward(10);",
      "static void draw(double n) { Turtle.forward(n); } static void unused() { Turtle.forward(99); }")
      .turtleCommandExecutionInfo
    assertEquals(result.completedCommands, List(forward(10), forward(20), forward(10)))
    assertEquals(result.nrOfCommandExecuted(forward(10)), 2)
    assertEquals(result.turtleCommandSitesInCodeOrder.map(_.literalCommand), List(Some(forward(10)), None, Some(forward(99))))
    assertEquals(result.nrOfCommandInCodeIgnoreParameter(forward(0)), 3)
    assertEquals(result.nrOfLiteralCommandInCode(forward(10)), 1)
    assertEquals(result.nrOfUnresolvedCommandSitesInCode(forward(0)), 1)
    assertEquals(result.nrOfLiteralCommandInCode(forward(99)), 1)
  }

  test("checked Java signed and widened literals remain exact source matches") {
    val result = java("Turtle.forward(-10); Turtle.forward(+20); Turtle.forward((30));").turtleCommandExecutionInfo
    List(-10.0, 20.0, 30.0).foreach { distance =>
      assertEquals(result.nrOfLiteralCommandInCode(forward(distance)), 1)
      assertEquals(result.nrOfCommandExecuted(forward(distance)), 1)
    }
  }

  test("checked Java preserves limit, cancellation and failure statuses with its source sites") {
    val infinite = java("while(true) { Turtle.forward(1); }")
    val limited = TurtleProgramAnalysis(infinite.expression, TurtleExecutionLimits(maxSteps = 20))
    assertEquals(limited.executionStatus, TurtleExecutionStatus.LimitExceeded)
    assertEquals(limited.turtleCommandSitesInCodeOrder.size, 1)
    intercept[IllegalArgumentException](limited.completedCommands)
    val cancelled = TurtleProgramAnalysis(infinite.expression, isCancelled = () => true)
    assertEquals(cancelled.executionStatus, TurtleExecutionStatus.Cancelled)
    val failed = java("Turtle.forward(1); int bad = 4 / 0;").turtleCommandExecutionInfo
    assert(failed.executionStatus.isInstanceOf[TurtleExecutionStatus.Failed])
    assertEquals(failed.executedTurtleCommandsInOrder, List(forward(1)))
  }

  test("invalid execution limits are rejected at construction") {
    intercept[IllegalArgumentException](TurtleExecutionLimits(maxSteps = 0))
    intercept[IllegalArgumentException](TurtleExecutionLimits(maxCommands = -1))
    intercept[IllegalArgumentException](TurtleExecutionLimits(maxCallDepth = 0))
    intercept[IllegalArgumentException](TurtleExecutionLimits(maxLoopIterations = 0))
  }
}
