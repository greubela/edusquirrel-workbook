package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import upickle.default.ReadWriter

/** Independent upper bounds. Missing commands are unlimited for that measure.
  * Keys currently identify turtle commands; other palette elements can be supported later.
  */
case class MaxCommandUsage(
    occurrencesInCode: Map[String, Int] = Map.empty,
    callsDuringExecution: Map[String, Int] = Map.empty
) derives ReadWriter {
  (occurrencesInCode.toList ++ callsDuringExecution.toList).foreach { (command, maximum) =>
    require(command != null && command.nonEmpty && command == command.trim, "Command limits need nonempty, trimmed command names.")
    require(maximum >= 0, s"The maximum usage of '$command' must not be negative.")
  }
}
