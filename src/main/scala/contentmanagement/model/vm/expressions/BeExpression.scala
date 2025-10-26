package contentmanagement.model.vm.expressions

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

trait BeExpression {
  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String

  def hasSideEffects: Boolean

  def getSyntaxErrors: Seq[BeInfo]

  def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState

  def canEvaluateTo: Set[BeDataType]

  def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock

}

object BeExpression {

  lazy val pass: BeExpression = new BeSequence(List(), true, Some(Set(BeDataType.Unit)))

  lazy val NoOp: BeExpression = new BeExpression {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ""

    override def hasSideEffects: Boolean = false

    override def getSyntaxErrors: Seq[BeInfo] = List()

    override def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = simulatorState

    override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

    override def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = ???
  }

}