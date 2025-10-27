package contentmanagement.model.vm.expressions.defining

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.expressions.{BeExpression, BeSequence, BeUseUnitValue, BeUseValue}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.BeChildRole.NoRole
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockStarter

case class BeStartProgram(startSequence: BeSequence) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = startSequence.getInLanguage(programmingLanguage, humanLanguage)

  override def hasSideEffects: Boolean = false

  override def getSyntaxErrors: Seq[BeInfo] = List()

  override def applySideEffects(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = startSequence.applySideEffects(config, simulatorState)

  override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

  override  def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = {
    BeBlockStarter(parentPos)
  }

  override def evaluateBlock(simulatorState: BeSimulatorState): BeUseValue = BeUseUnitValue
  
  def createBlock(config: BeDisplayConfig): BeBlock = createBlock(config, BeChildPosition(NodeBasedTreePosition.root, BeChildRole.NoRole))

  override def getChildren: List[(BeChildRole, BeExpression)] = List(
    (BeChildRole.BodySequence(0), startSequence)
  )

  override val toString: String = {
    s"""BeStartProgram(
       |  $startSequence
       |)""".stripMargin
  }

}
