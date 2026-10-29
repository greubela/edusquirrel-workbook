package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog
import upickle.default.ReadWriter

case class ProgrammingTestProgramObservation(commandCount: Int, commands: List[TurtleCommand[Double]]) derives ReadWriter {
  import ProgrammingTestProgramObservation.*

  require(commandCount >= 0 && commandCount <= MaxCommands, "Invalid observed command count.")
  require(commands != null && commands.size == math.min(PrefixLength, commandCount), "Invalid observed command prefix.")
  require(commands.forall(command => command != null && command.name != null && command.args != null && command.stringArgs != null &&
    command.stringArgs.isEmpty && commandArity(command.name).contains(command.args.size) &&
    command.args.forall(value => value.isFinite && math.abs(value) <= MaxMagnitude)), "Invalid observed command.")

  def omittedCommands: Int = commandCount - commands.size
}

object ProgrammingTestProgramObservation {
  private[evadid] val MaxCommands = 10000
  private[evadid] val PrefixLength = 32
  private[evadid] val MaxMagnitude = 9007199254740991d
  private val numericArities = SnapTurtleCatalog.Primitives.filter(primitive =>
    primitive.snapSelector != "receiveGo" && primitive.inputKinds.forall(_ == SnapTurtleCatalog.SnapInputKind.Numeric))
    .map(primitive => primitive.turtleCommand -> primitive.arity).toMap

  private[evadid] def commandArity(name: String): Option[Int] =
    Option(name).filter(_.length <= 64).flatMap(numericArities.get)
}
