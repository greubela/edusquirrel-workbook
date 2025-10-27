package contentmanagement.model.vm.expressions.defining

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.expressions.{BeExpression, BeSequence}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.BeChildRole.NoRole
import contentmanagement.model.vm.types.{BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockStarter

case class BeStartProgram(startSequence: BeSequence) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = startSequence.getInLanguage(programmingLanguage, humanLanguage)

  override def hasSideEffects: Boolean = false

  override def getSyntaxErrors: Seq[BeInfo] = List()

  override def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = startSequence.execute(config, simulatorState)

  override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

  override def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = {
    BeBlockStarter()
  }

  def createBlock(config: BeDisplayConfig): BeBlock = createBlock(config, NoRole)

  override def getChildren: List[(BeChildRole, BeExpression)] = List(
    (BeChildRole.BodySequence(), startSequence)
  )

  override val toString: String = {
    s"""BeStartProgram(
       |  $startSequence
       |)""".stripMargin
  }

}
