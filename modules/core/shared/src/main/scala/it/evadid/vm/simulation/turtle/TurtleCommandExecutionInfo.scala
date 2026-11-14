package it.evadid.vm.simulation.turtle

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.naming.BeEntityName
import upickle.default.ReadWriter

/** An authored occurrence. Paths identify occurrences within this expression tree,
  * including identical blocks, unused definitions and both conditional branches.
  * Argument expressions are retained without substituting runtime variable values.
  */
case class TurtleCommandSite[T](
    path: Vector[Int],
    name: BeEntityName,
    argumentExpressions: Vector[BeExpression],
    literalCommand: Option[TurtleCommand[T]])

/** Counts describe the recorded execution; check executionStatus before treating
  * them as totals. Source counts are independent of execution completion.
  */
case class TurtleCommandExecutionInfo[T](
    executedTurtleCommandsInOrder: List[TurtleCommand[T]],
    turtleCommandSitesInCodeOrder: List[TurtleCommandSite[T]],
    executionStatus: TurtleExecutionStatus) {

  def nrOfCommandExecuted(command: TurtleCommand[T]): Int =
    executedTurtleCommandsInOrder.count(_ == command)

  def nrOfCommandExecutedIgnoreParameter(command: TurtleCommand[T]): Int =
    nrOfCommandExecutedIgnoreParameter(command.name)

  def nrOfCommandExecutedIgnoreParameter(name: BeEntityName): Int = {
    val canonical = TurtleCommand.canonicalName(name)
    executedTurtleCommandsInOrder.count(command => TurtleCommand.canonicalName(command.name) == canonical)
  }

  def nrOfCommandInCodeIgnoreParameter(command: TurtleCommand[T]): Int =
    nrOfCommandInCodeIgnoreParameter(command.name)

  def nrOfCommandInCodeIgnoreParameter(name: BeEntityName): Int =
    turtleCommandSitesInCodeOrder.count(_.name == TurtleCommand.canonicalName(name))

  /** Symbolic arguments do not count as literal matches. */
  def nrOfLiteralCommandInCode(command: TurtleCommand[T]): Int =
    turtleCommandSitesInCodeOrder.count(_.literalCommand.contains(command))

  def nrOfUnresolvedCommandSitesInCode(command: TurtleCommand[T]): Int =
    turtleCommandSitesInCodeOrder.count(site => site.name == TurtleCommand.canonicalName(command.name) && site.literalCommand.isEmpty)

  /** Drawing/grading callers must not silently accept a partial execution. */
  def completedCommands: List[TurtleCommand[T]] = {
    require(executionStatus == TurtleExecutionStatus.Completed, s"Turtle execution did not complete: $executionStatus.")
    executedTurtleCommandsInOrder
  }
}

enum TurtleExecutionStatus derives ReadWriter {
  case Completed, LimitExceeded, Cancelled
  case Failed(message: String)
}

case class TurtleExecutionLimits(
    maxSteps: Int = 100000,
    maxCommands: Int = 10000,
    maxCallDepth: Int = 64,
    maxLoopIterations: Int = 10000) derives ReadWriter {
  require(maxSteps > 0 && maxCommands >= 0 && maxCallDepth > 0 && maxLoopIterations > 0,
    "Turtle execution limits must be positive (the command limit may be zero).")
}
