package contentmanagement.model.vm.types

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.code.usage.*
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.BeDataType.{BeUnionAllowedTypes, BeUnionType}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo, BeScope}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.variable.*

trait BeDataValue { //extends BeExpression {

  def possibleTypes: BeDataType

  def displayAsString: String

}

case class BeDataValueLiteral(literalString: String) extends BeDataValue {

  override val possibleTypes: BeDataType = {
    val possibleTypes = BeDataType.allKnownTypesThatHaveLiterals.filter(_.isValidLiteral(literalString))
    if (possibleTypes.size == 1) possibleTypes.head
    else if (possibleTypes.nonEmpty) BeUnionAllowedTypes(possibleTypes)
    else BeDataType.Error
  }

  val displayAsString: String = literalString

}

case class BeDataValueUnit() extends BeDataValue {
  def formatValueForDisplay: LanguageMap[ProgrammingLanguage] = LanguageMap.universalMap("")

  val displayAsString: String = ""

  val possibleTypes: BeDataType = BeDataType.Unit
}


/*


object BeUseUnitValue extends BeDataValue {
  def getCurrentValueAsString(simulatorState: BeSimulatorState): Option[String] = Some("")

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ""

  def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()


  def canEvaluateTo: BeDataType = BeDataType.Unit

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = BeBlockUseLiteral(BeUseValueLiteral("", None), parentPos)
}

object BeUseNonExistingValue extends BeDataValue {

  def getCurrentValueAsString(simulatorState: BeSimulatorState): Option[String] = None

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = "[missing value]"

  def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List(BeInfo(LanguageMap.universalMap("missing value"), BeInfo.SyntaxError.MissingValue))

  def canEvaluateTo: BeDataType = BeDataType.Error

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = BeBlockPlaceholderMissingValue(this, parentPos)
}

case class BeUseValueReferencing(referencedVariable: BeDefineVariable) extends BeDataValue {

  def getCurrentValueAsString(simulatorState: BeSimulatorState): Option[String] = {
    val curValueInMachine = simulatorState.machineState.variableValues.get(referencedVariable)
    if (curValueInMachine.nonEmpty) curValueInMachine.get.getCurrentValueAsString(simulatorState) else None
  }

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = referencedVariable.name.getInLanguage(humanLanguage)

  def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()

  def canEvaluateTo: BeDataType = referencedVariable.canEvaluateTo

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = BeBlockUseReference(this, parentPos)
}

case class BeUseValueLiteral(value: String, optionalContext: Option[BeDefineVariable] = None) extends BeDataValue {

  def getCurrentValueAsString(simulatorState: BeSimulatorState): Option[String] = Some(value)

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String =
    canEvaluateTo.formatStringForDisplay(value)

  def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()

  lazy val canEvaluateTo: BeDataType = BeDataType.allPossibleAtomicTypesForLiteral(value)

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock =
    if (optionalContext.nonEmpty) {
      BeBlockUseLiteralForVariable(this, optionalContext.get, parentPos)
    } else {
      BeBlockUseLiteral(this, parentPos)
    }


  override def toString: String =
    if (optionalContext.nonEmpty) "BeUseValueLiteral(" + optionalContext.get.name.toString + " <- " + value + ")"
    else "BeUseValueLiteral(" + value + ")"

}

// todo case class BeUseValueObject

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
*/