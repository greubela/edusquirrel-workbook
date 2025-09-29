package interactionPlugins.blockProgramming

case class TurtleProgramDiff(missingCommands: List[String], additionalCommands: List[String]) {
  def isPerfectMatch: Boolean = missingCommands.isEmpty && additionalCommands.isEmpty

  def humanReadableSummary: String = {
    val missingPart = if (missingCommands.isEmpty) None else Some("Missing: " + missingCommands.mkString(", "))
    val additionalPart = if (additionalCommands.isEmpty) None else Some("Unexpected: " + additionalCommands.mkString(", "))
    (missingPart.toList ++ additionalPart.toList).mkString(" | ")
  }
}

object TurtleProgramDiff {
  def compare(sample: TurtleProgramState, attempt: TurtleProgramState): TurtleProgramDiff = {
    val expected = sample.commands.groupBy(identity).view.mapValues(_.size).toMap
    val actual = attempt.commands.groupBy(identity).view.mapValues(_.size).toMap

    val missing = expected.flatMap { case (cmd, count) =>
      val diff = count - actual.getOrElse(cmd, 0)
      if (diff > 0) List.fill(diff)(cmd.programText) else Nil
    }.toList

    val additional = actual.flatMap { case (cmd, count) =>
      val diff = count - expected.getOrElse(cmd, 0)
      if (diff > 0) List.fill(diff)(cmd.programText) else Nil
    }.toList

    TurtleProgramDiff(missing, additional)
  }
}
