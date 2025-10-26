package contentmanagement.model.vm.types

import contentmanagement.model.vm.*

/*

case class BeValueMissing(associatedVariable: BeVariable) extends BeValue {

  override def currentValue(simulator: BeSimulatorState): Option[String] = None

  def getSyntaxErrors: Seq[BeInfo] =
    List(BeInfo(LanguageMap.universalMap("missing value"), BeInfo.SyntaxError.MissingValue))

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