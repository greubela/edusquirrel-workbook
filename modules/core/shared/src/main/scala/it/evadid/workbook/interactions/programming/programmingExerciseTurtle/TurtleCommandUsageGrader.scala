package it.evadid.workbook.interactions.programming.programmingExerciseTurtle


import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, WorkbookContent}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.simulation.turtle.TurtleProgramAnalysis
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.interactions.programming.{AdditionalProgrammingTestInfo, ProgrammingTestGrader}
import it.evadid.workbook.interactions.programming.state.ProgrammingState
import it.evadid.workbook.abstractions.ExecutionContextInformation

import scala.concurrent.Future
import upickle.default.ReadWriter

import java.time.Instant

enum CommandUsageMeasure derives ReadWriter {
  @upickle.implicits.key("OccurrencesInCode") case OccurrencesInCode
  @upickle.implicits.key("CallsDuringExecution") case CallsDuringExecution
}

/** One independently stored grading result for one command and one measure. */
case class TurtleCommandUsageGrader(
    command: BeEntityName,
    maximum: Int,
    measure: CommandUsageMeasure,
    testId: String,
    importance: GradingImportance = GradingImportance.REQUIRED_TEST,
    /** The libraries the exercise provides; its exercise sets it ([[ProgrammingTestGrader.inEnvironment]]). */
    environment: ProgrammingEnvironment = ProgrammingEnvironment.all
) extends ProgrammingTestGrader derives ReadWriter {
  def persistedType: String = TurtleCommandUsageGrader.codec.persistedType
  require(maximum >= 0, "A command usage maximum must not be negative.")
  override def inEnvironment(environment: ProgrammingEnvironment): TurtleCommandUsageGrader = copy(environment = environment)
  require(testId != null && testId.nonEmpty && testId == testId.trim, "A programming test needs a nonempty ID.")
  private val canonicalCommand = TurtleCommand.resolveCanonicalNames(command)
    .getOrElse(throw IllegalArgumentException(s"Unknown turtle command: ${command.universalInterpretation()}"))

  override val runningBehavior: GraderRunningBehavior = GraderRunningBehavior.RUN_ON_AT_LEAST_MINOR
  override val gradingEntity: FeedbackEntity = FeedbackEntity.TestEntity

  override protected def handleGrading(state: ProgrammingState, contextInformation: ExecutionContextInformation)
      : Future[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]] = Future {
    val program = state.inEnvironment(environment).expression
    val expression = program.expression
    val malformed = expression.subtree.exists {
      case _: BeExpressionUnparsable | _: BeExpressionUnsupported => true
      case _ => false
    }
    require(!malformed, "Command usage cannot be determined for an unsupported or unfinished program.")
    val count = measure match {
      case CommandUsageMeasure.OccurrencesInCode =>
        program.turtleCommandSites.count(_.name == canonicalCommand)
      case CommandUsageMeasure.CallsDuringExecution =>
        val info = program.turtleCommandExecutionInfo
        val _ = info.completedCommands // A partial trace must never satisfy an execution limit.
        info.nrOfCommandExecutedIgnoreParameter(canonicalCommand)
    }
    val status = if count <= maximum then GradingStatus.Correct else GradingStatus.Incorrect
    val res: GradingResult[ProgrammingState, AdditionalProgrammingTestInfo] =
     GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](this, state, status, Some(WorkbookContent(description(count))), None, Instant.now())
    res
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
  lazy val codec: WorkbookInteractionGrader.Codec[TurtleCommandUsageGrader] = WorkbookInteractionGrader.codec("TurtleCommandUsageGrader")
  def forLimits(elementId: String, limits: MaxCommandUsage): Seq[TurtleCommandUsageGrader] =
    List(CommandUsageMeasure.OccurrencesInCode -> limits.occurrencesInCode,
      CommandUsageMeasure.CallsDuringExecution -> limits.callsDuringExecution).flatMap { (measure, entries) =>
      entries.toList.sortBy(_._1).map { (name, maximum) =>
        val canonical = TurtleCommand.resolveCanonicalNames(name)
          .getOrElse(throw IllegalArgumentException(s"Unknown turtle command in usage limits: $name"))
        TurtleCommandUsageGrader(canonical, maximum, measure, s"$elementId-command-$name-$measure")
      }
    }
}
