package it.evadid.vm.simulation.turtle

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.simulation.BeProgramInterpreter

/**
 * Turtle view of the shared interpreter: authored call sites plus the drawing of one startup run.
 * Partial commands are retained and the completion status is reported explicitly.
 */
object TurtleProgramAnalysis {

  def apply(expression: BeExpression, limits: TurtleExecutionLimits = TurtleExecutionLimits(),
      isCancelled: () => Boolean = () => false): TurtleCommandExecutionInfo[Double] = {
    val execution = BeProgramInterpreter.run(expression, limits = limits, isCancelled = isCancelled)
    TurtleCommandExecutionInfo(execution.turtleCommands, commandSites(expression), execution.status)
  }

  /** Static analysis can also be requested without executing the program. */
  def commandSites(expression: BeExpression): List[TurtleCommandSite[Double]] = TurtleCommandSites(expression)

  def toPathBuilder(expression: BeExpression, startPoint: Point[Double] = Point(0.0, 0.0),
      headingDeg: Double = TurtlePathBuilder.SnapHeadingDeg): TurtlePathBuilder[Double] =
    TurtlePathBuilder(startPoint, apply(expression).completedCommands, headingDeg)
}
