package contentmanagement.model.vm.types

import contentmanagement.model.language.AppLanguage
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.types.BeDataType.BeUnionAllowedTypes

trait BeDataValue {

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

  val displayAsString: String = ""

  val possibleTypes: BeDataType = BeDataType.Unit
}

case class BeUseValueReference(variable: BeDefineVariable) extends BeDataValue {

  override def possibleTypes: BeDataType = variable.variableType

  override def displayAsString: String = variable.name.getInLanguage(AppLanguage.default())
}

case class BeUseValueLiteral(value: String, optionalContext: Option[BeDefineVariable] = None) extends BeDataValue {

  def displayAsString: String = value

  lazy val possibleTypes: BeDataType = BeDataValueLiteral(value).possibleTypes
}
