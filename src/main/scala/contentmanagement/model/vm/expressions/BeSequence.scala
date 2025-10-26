package contentmanagement.model.vm.expressions

import contentmanagement.datastructures.tree.TreeStructureContext
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockSequence

case class BeSequence(body: List[BeExpression], mayBeEmpty: Boolean, evaluateNotToLastElementButTo: Option[Set[BeDataType]]) extends BeExpression {

  def hasSideEffects: Boolean = body.exists(_.hasSideEffects)

  def getSyntaxErrors: Seq[BeInfo] = {
    if (mayBeEmpty && body.isEmpty) List(BeInfo(LanguageMap.universalMap("Sequence must not be empty but is empty!"), BeInfo.SyntaxError.MissingValue))
    else List()
  }

  def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = {
    var currentState = simulatorState
    for (curExpression <- body) {
      currentState = curExpression.execute(config, currentState)
    }
    currentState
  }

  def canEvaluateTo: Set[BeDataType] = if (evaluateNotToLastElementButTo.nonEmpty) evaluateNotToLastElementButTo.get else if (body.nonEmpty) body.last.canEvaluateTo else Set(BeDataType.Error)

  def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = BeBlockSequence(this, roleInParent)

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = body.map(_.getInLanguage(programmingLanguage, humanLanguage)).mkString("\n")
}
