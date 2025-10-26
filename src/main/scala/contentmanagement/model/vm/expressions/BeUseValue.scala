package contentmanagement.model.vm.expressions

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.expressions.defining.BeDefineVariable
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.variable.{BeBlockPlaceholderMissingValue, BeBlockUseLiteral, BeBlockUseLiteralForVariable, BeBlockUseReference}

trait BeUseValue extends BeExpression {

  def getCurrentValue(simulatorState: BeSimulatorState): Option[String]

  override def hasSideEffects: Boolean = false

  def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = simulatorState
}


object BeUseNonExistingValue extends BeUseValue {

  def getCurrentValue(simulatorState: BeSimulatorState): Option[String] = None

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = "[missing value]"

  def getSyntaxErrors: Seq[BeInfo] = List(BeInfo(LanguageMap.universalMap("missing value"), BeInfo.SyntaxError.MissingValue))

  def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Error)

  def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = BeBlockPlaceholderMissingValue(this, roleInParent)

}

case class BeUseValueReferencing(referencedVariable: BeDefineVariable) extends BeUseValue {

  def getCurrentValue(simulatorState: BeSimulatorState): Option[String] = {
    val curValueInMachine = simulatorState.machineState.variableValues.get(referencedVariable)
    if (curValueInMachine.nonEmpty) curValueInMachine.get.getCurrentValue(simulatorState) else None
  }

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = referencedVariable.name.getInLanguage(humanLanguage)

  def getSyntaxErrors: Seq[BeInfo] = List()

  def canEvaluateTo: Set[BeDataType] = referencedVariable.canEvaluateTo

  def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = BeBlockUseReference(this, roleInParent)
}

case class BeUseValueLiteral(value: String) extends BeUseValue {

  def getCurrentValue(simulatorState: BeSimulatorState): Option[String] = Some(value)

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = canEvaluateTo.headOption.map(_.formatStringForDisplay(value)).getOrElse(value)

  def getSyntaxErrors: Seq[BeInfo] = List()

  lazy val canEvaluateTo: Set[BeDataType] = BeDataType.allPossibleTypesForLiteral(value)

  def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = BeBlockUseLiteral(this, roleInParent)
}

/*

case class BeValueMissing(associatedVariable: BeVariable) extends BeValue {

  override def currentValue(simulator: BeSimulatorState): Option[String] = None

  def getSyntaxErrors: Seq[BeInfo] =

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = "[[[BeBlockInfo: VAL MISSING]]]"

  def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = simulatorState

  def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Error)

  def createBlock(displayConfig: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = BeBlockPlaceholderMissingValue(associatedVariable, roleInParent)

}

// todo remove associated variable and move that to role
case class BeValueLiteral(value: String, associatedVariable: BeVariable) extends BeValue() {

  val typesThatSatisfyVariableAndValue: Set[BeDataType] = associatedVariable.canTakeTypes.filter(_.isValidLiteral(value))

  def displayString: String = if (typesThatSatisfyVariableAndValue.nonEmpty)
    typesThatSatisfyVariableAndValue.head.formatStringForDisplay(value)
  else value

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = displayString

  override def currentValue(simulator: BeSimulatorState): Option[String] = Some(value)

  def canEvaluateTo: Set[BeDataType] = {
    val res = typesThatSatisfyVariableAndValue
    if (res.isEmpty) Set(BeDataType.Error) else res
  }

  def getSyntaxErrors: Seq[BeInfo] =
    if (typesThatSatisfyVariableAndValue.nonEmpty) List() else
      List(BeInfo(LanguageMap.universalMap("invalid literal: <" + value + "> is none of the allowed types: " + associatedVariable.canTakeTypes), BeInfo.SyntaxError.InvalidLiteralValue))

  def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = simulatorState

  def createBlock(displayConfig: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = BeBlockUseLiteralForVariable(this, roleInParent)
}

case class BeValueReference(associatedVariable: BeVariable) extends BeValue {

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = associatedVariable.toDisplayName.getInLanguage(humanLanguage)

  def canEvaluateTo: Set[BeDataType] = associatedVariable.canTakeTypes


  def getSyntaxErrors: Seq[BeInfo] = List()

  override def currentValue(simulator: BeSimulatorState): Option[String] = {
    val lastState = simulator.states.last
    val variableState: Option[BeValue] = lastState.definedVariables.get(associatedVariable)
    if (variableState.nonEmpty) variableState.flatMap(_.currentValue(simulator))
    else None
  }

  def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = simulatorState


  def createBlock(displayConfig: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = BeBlockUseReference(this, roleInParent)

}*/
