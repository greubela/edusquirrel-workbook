package contentmanagement.model.vm.types

import contentmanagement.model.language.AppLanguage
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.types.BeDataType.BeUnionAllowedTypes

trait BeDataValue {

  def possibleTypes: BeDataType

  def displayAsString: String
}


case class BeDataValueUnit() extends BeDataValue {

  val displayAsString: String = ""

  val possibleTypes: BeDataType = BeDataType.Unit
}

case class BeUseValueReference(variable: BeDefineVariable) extends BeDataValue {

  override def possibleTypes: BeDataType = variable.variableType

  override def displayAsString: String = variable.name.getInLanguage(AppLanguage.default())
}

case class BeDataValueLiteral(literalString: String) extends BeDataValue {
  def displayAsString: String = literalString

  override val possibleTypes: BeDataType = {
    val possibleTypes = BeDataType.allKnownTypesThatHaveLiterals.filter(_.isValidLiteral(literalString))
    if (possibleTypes.size == 1) possibleTypes.head
    else if (possibleTypes.nonEmpty) BeUnionAllowedTypes(possibleTypes)
    else BeDataType.Error
  }
}
