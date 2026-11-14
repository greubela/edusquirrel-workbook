package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.conversion.ProgrammingStateConversions.*
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.simulation.turtle.TurtleProgramAnalysis
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.programming.{AdditionalProgrammingTestInfo, ProgrammingTestGrader}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.meta.ExecutionContextInformation
import scala.concurrent.Future
import upickle.default.ReadWriter

enum CommandUsageMeasure derives ReadWriter {
  case OccurrencesInCode, CallsDuringExecution
}

/** One independently stored grading result for one command and one measure. */
case class TurtleCommandUsageGrader(
    command: BeEntityName,
    maximum: Int,
    measure: CommandUsageMeasure,
    testId: String,
    importance: GradingImportance = GradingImportance.REQUIRED_TEST
) extends ProgrammingTestGrader derives ReadWriter {
  require(maximum >= 0, "A command usage maximum must not be negative.")
  require(testId != null && testId.nonEmpty && testId == testId.trim, "A programming test needs a nonempty ID.")
  private val canonicalCommand = TurtleCommand.resolveCanonicalNames(command)
    .getOrElse(throw IllegalArgumentException(s"Unknown turtle command: ${command.universalInterpretation()}"))

  override val runningBehavior: GraderRunningBehavior = GraderRunningBehavior.RUN_ALWAYS
  override val gradingEntity: FeedbackEntity = FeedbackEntity.TestEntity()

  override protected def handleGrading(state: ProgrammingState, contextInformation: ExecutionContextInformation)
      : Future[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]] = Future {
    val expression = state.toBeExpression.expression
    val malformed = expression.recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values.exists {
      case BeExpressionReference(_, _: BeExpressionUnparsable | _: BeExpressionUnsupported) => true
      case _ => false
    }
    require(!malformed, "Command usage cannot be determined for an unsupported or unfinished program.")
    val count = measure match {
      case CommandUsageMeasure.OccurrencesInCode =>
        TurtleProgramAnalysis.commandSites(expression).count(_.name == canonicalCommand)
      case CommandUsageMeasure.CallsDuringExecution =>
        val info = TurtleProgramAnalysis(expression)
        val _ = info.completedCommands // A partial trace must never satisfy an execution limit.
        info.nrOfCommandExecutedIgnoreParameter(canonicalCommand)
    }
    val status = if count <= maximum then GradingStatus.Correct() else GradingStatus.Incorrect()
    GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](this, state, status,
      feedbackInformation = Some(description(count)))
  }(using contextInformation.futureExecutionContext)

  private def description(count: Int): LanguageMap[HumanLanguage] =
    LanguageMap.mapBasedLanguageMap(AppLanguage.humanLanguages.map { language =>
      val label = canonicalCommand.getNameIn(language, NamingStyle.SnakeCase)
      val source = measure == CommandUsageMeasure.OccurrencesInCode
      val message = language match {
        case German => s"$label: $count ${if source then "Vorkommen im Code" else "ausgeführte Aufrufe"}; höchstens $maximum erlaubt."
        case Danish => s"$label: $count ${if source then "forekomster i koden" else "udførte kald"}; højst $maximum tilladt."
        case French => s"$label : $count ${if source then "occurrences dans le code" else "appels exécutés"} ; maximum autorisé : $maximum."
        case Spanish => s"$label: $count ${if source then "apariciones en el código" else "llamadas ejecutadas"}; máximo permitido: $maximum."
        case Ukrainian => s"$label: $count ${if source then "входжень у коді" else "виконаних викликів"}; дозволено щонайбільше $maximum."
        case Russian => s"$label: $count ${if source then "вхождений в коде" else "выполненных вызовов"}; разрешено не более $maximum."
        case Turkish => s"$label: ${if source then "koddaki kullanım" else "çalıştırılan çağrı"} sayısı $count; izin verilen en fazla $maximum."
        case _ => s"$label: $count ${if source then "occurrences in code" else "executed calls"}; at most $maximum allowed."
      }
      language -> message
    }.toMap)
}

object TurtleCommandUsageGrader {
  def forLimits(elementId: String, limits: MaxCommandUsage): List[TurtleCommandUsageGrader] =
    List(CommandUsageMeasure.OccurrencesInCode -> limits.occurrencesInCode,
      CommandUsageMeasure.CallsDuringExecution -> limits.callsDuringExecution).flatMap { (measure, entries) =>
      entries.toList.sortBy(_._1).map { (name, maximum) =>
        val canonical = TurtleCommand.resolveCanonicalNames(name)
          .getOrElse(throw IllegalArgumentException(s"Unknown turtle command in usage limits: $name"))
        TurtleCommandUsageGrader(canonical, maximum, measure, s"$elementId-command-$name-$measure")
      }
    }
}
