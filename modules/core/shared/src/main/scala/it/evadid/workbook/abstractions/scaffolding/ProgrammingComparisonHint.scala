package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingTestInvocation
import scala.concurrent.{ExecutionContext, Future}

private[scaffolding] object ProgrammingComparisonHint {
  def render(invocation: ProgrammingTestInvocation, language: HumanLanguage,
      resolver: LanguageMapIdResolver)(using ExecutionContext): Future[String] = {
    val comparison = invocation.commandComparison.get
    val commandLabels = Map("forward" -> "programmingHintForward", "right" -> "programmingHintRight", "left" -> "programmingHintLeft")
    val commands = comparison.actual.toList ++ comparison.expected.toList
    val scopeKey = if invocation.target.isDefined then "programmingHintCall" else "programmingHintProgram"
    val samePosition = comparison.actual.isDefined && comparison.expected.isDefined
    val messageKey = if samePosition then "programmingHintCommandDifference" else "programmingHintCommandCount"
    val detailKeys = if samePosition then commands.flatMap(command => commandLabels.get(command.name))
      else List("programmingHintOneCommand", "programmingHintCommands")
    val keys = (scopeKey :: messageKey :: detailKeys).distinct
    Future.traverse(keys)(key => resolver.resolveMap(LanguageMapContentId("basic", key)).map(key -> _)).map { resolved =>
      val translations = resolved.toMap
      def text(key: String, arguments: String*): String = {
        val template = translations(key).tryGetInLanguage(language).orElse(translations(key).tryGetInLanguage(English))
          .filter(value => value != null && value.nonEmpty && value.length <= 1024)
          .getOrElse(throw IllegalArgumentException("A programming hint translation is missing."))
        val parts = template.split("_", -1).toVector
        require(parts.size == arguments.size + 1, "Invalid programming hint placeholders.")
        parts.zipAll(arguments, "", "").map((part, argument) => part + argument).mkString
      }
      def number(value: Double): String =
        if value == 0 && java.lang.Double.doubleToRawLongBits(value) < 0 then "-0.0"
        else if value == math.rint(value) then value.toLong.toString
        else java.lang.Double.toString(value)
      def command(value: TurtleCommand[Double]): String = commandLabels.get(value.name) match {
        case Some(key) => text(key, value.args.map(number)*)
        case None => s"${value.name}(${value.args.map(number).mkString(", ")})"
      }
      def count(value: Int): String =
        if value == 1 then text("programmingHintOneCommand") else text("programmingHintCommands", value.toString)
      val scope = invocation.target match {
        case None => text(scopeKey)
        case Some(target) =>
          require(!target.exists(char => Character.isISOControl(char) || char == '\u2028' || char == '\u2029'), "Invalid invocation label.")
          val arguments = invocation.arguments.map {
            case ProgrammingTestInvocation.Scalar.Bool(value) => value.toString
            case ProgrammingTestInvocation.Scalar.Integer(value) => value
            case ProgrammingTestInvocation.Scalar.Float64(bits) =>
              val value = java.lang.Double.toString(java.lang.Double.longBitsToDouble(java.lang.Long.parseUnsignedLong(bits, 16)))
              if bits == "8000000000000000" then "-0.0"
              else if value.matches("-?[0-9]+") then s"$value.0" else value
          }
          text(scopeKey, s"$target(${arguments.mkString(", ")})")
      }
      val message = if samePosition then text(messageKey, scope, (comparison.commandIndex + 1).toString,
        command(comparison.actual.get), command(comparison.expected.get))
      else text(messageKey, scope, count(comparison.actualCount), count(comparison.expectedCount))
      require(message.length <= 2048 && !message.exists(char => Character.isISOControl(char) || char == '\u2028' || char == '\u2029'),
        "Invalid programming hint text.")
      message
    }
  }
}
