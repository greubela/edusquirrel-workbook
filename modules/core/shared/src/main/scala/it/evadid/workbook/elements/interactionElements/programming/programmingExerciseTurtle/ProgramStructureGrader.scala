package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.simulation.turtle.TurtleProgramAnalysis
import it.evadid.vm.code.controlStructures.{BeFor, BeRepeatNr, BeWhile}
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.programming.{AdditionalProgrammingTestInfo, ProgrammingTestGrader}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.meta.ExecutionContextInformation
import scala.concurrent.Future
import upickle.default.ReadWriter

/** Structural requirements are inspected in parsed programs, never by searching source text. */
enum ProgramStructureRequirement derives ReadWriter {
  case UsesLoop, NoLoops, NoFunctions
  case OnlyCommands(commands: Seq[String])
  case DefinesFunction(name: String, parameters: Seq[String] = Seq.empty)
}

case class ProgramStructureGrader(
    requirement: ProgramStructureRequirement,
    testId: String,
    publicDescription: LanguageMapContentId,
    importance: GradingImportance = GradingImportance.REQUIRED_TEST
) extends ProgrammingTestGrader derives ReadWriter {
  require(testId.nonEmpty && testId == testId.trim, "A structure test needs a nonempty ID.")
  override val runningBehavior = GraderRunningBehavior.RUN_ALWAYS
  override val gradingEntity = FeedbackEntity.TestEntity()

  override protected def handleGrading(state: ProgrammingState, context: ExecutionContextInformation)
      : Future[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]] = Future {
    val passed = satisfies(state.toBeExpression.expression)
    GradingResult(this, state, if passed then GradingStatus.Correct() else GradingStatus.Incorrect())
  }(using context.futureExecutionContext)

  private def nodesOf(expression: BeExpression): Seq[BeExpression] = {
    val nodes = expression.recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values.collect {
      case BeExpressionReference(_, node) => node
    }.toSeq
    require(!nodes.exists {
      case _: BeExpressionUnparsable | _: BeExpressionUnsupported => true
      case _ => false
    }, "Program structure cannot be checked until the program can be parsed.")
    nodes
  }

  private def containsLoop(nodes: Seq[BeExpression]): Boolean = nodes.exists {
      case _: BeRepeatNr | _: BeFor | _: BeWhile => true
      case _ => false
    }

  private def functionSignatures(nodes: Seq[BeExpression]): Seq[(String, Seq[String])] = {
    nodes.collect {
      case function: BeDefineFunction if !BeProgram.isUnusedCode(function) =>
        SnapExpressionSupport.pythonNameOf(function) -> function.inputs.map(_.name.universalInterpretation())
    }
  }

  private def satisfies(expression: BeExpression): Boolean = {
    val nodes = nodesOf(expression)
    requirement match {
      case ProgramStructureRequirement.OnlyCommands(commands) =>
        val allowed = commands.map(name => TurtleCommand.resolveCanonicalNames(name)
          .getOrElse(throw IllegalArgumentException(s"Unknown turtle command: $name"))).toSet
        TurtleProgramAnalysis.commandSites(expression).forall(site => allowed(site.name))
      case ProgramStructureRequirement.UsesLoop => containsLoop(nodes)
      case ProgramStructureRequirement.NoLoops => !containsLoop(nodes)
      case ProgramStructureRequirement.NoFunctions => functionSignatures(nodes).isEmpty
      case ProgramStructureRequirement.DefinesFunction(name, parameters) =>
        functionSignatures(nodes).exists((actual, inputs) => actual == name && parameters.forall(inputs.contains))
    }
  }
}
