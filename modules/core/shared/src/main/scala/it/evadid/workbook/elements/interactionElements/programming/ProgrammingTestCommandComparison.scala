package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import scala.util.Try
import upickle.default.ReadWriter

case class ProgrammingTestCommandComparison(actualCount: Int, expectedCount: Int, commandIndex: Int,
    actual: Option[TurtleCommand[Double]], expected: Option[TurtleCommand[Double]]) derives ReadWriter {
  import ProgrammingTestCommandComparison.*

  require(actualCount >= 0 && actualCount <= MaxCommands && expectedCount >= 0 && expectedCount <= MaxCommands,
    "Invalid compared command count.")
  require(commandIndex >= 0 && commandIndex < math.max(actualCount, expectedCount), "Invalid differing command index.")
  require(actual != null && expected != null && actual.isDefined == (commandIndex < actualCount) &&
    expected.isDefined == (commandIndex < expectedCount), "Missing compared command.")
  require(actual.toList.concat(expected.toList).forall(validCommand), "Invalid compared command.")
  require(actual != expected && (actual.isDefined && expected.isDefined || commandIndex == math.min(actualCount, expectedCount)),
    "Commands must differ at the reported position.")
}

object ProgrammingTestCommandComparison {
  private[evadid] val MaxCommands = 128

  private def validCommand(command: TurtleCommand[Double]): Boolean =
    command != null && command.args != null && command.stringArgs != null && command.stringArgs.isEmpty &&
      ProgrammingTestProgramObservation.commandArity(command.name).contains(command.args.size) &&
      command.args.forall(value => value.isFinite && math.abs(value) <= ProgrammingTestProgramObservation.MaxMagnitude)

  private[evadid] def fromReport(value: ujson.Value): Option[ProgrammingTestCommandComparison] = Try {
    require(value.obj.keySet.toSet == Set("actualCount", "expectedCount", "commandIndex", "actual", "expected"))
    def count(key: String): Int = {
      val number = value(key).num
      require(number.isValidInt)
      number.toInt
    }
    def command(key: String): Option[TurtleCommand[Double]] = value(key) match {
      case ujson.Null => None
      case row =>
        val parts = row.arr
        require(parts.nonEmpty && parts.size <= 4)
        Some(TurtleCommand[Double](parts.head.str, parts.tail.map(_.num).toList))
    }
    ProgrammingTestCommandComparison(count("actualCount"), count("expectedCount"), count("commandIndex"),
      command("actual"), command("expected"))
  }.toOption
}
