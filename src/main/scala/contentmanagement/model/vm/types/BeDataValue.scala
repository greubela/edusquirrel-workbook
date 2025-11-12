package contentmanagement.model.vm.types

import contentmanagement.model.language.AppLanguage
import contentmanagement.model.vm.code.defining.BeDefineVariable
import scala.util.Try

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
    val trimmed = literalString.trim
    def isNumericLiteral(str: String): Boolean =
      BeDataType.Numeric.isValidLiteral(str) || Try(BigDecimal(str)).isSuccess

    if (isNumericLiteral(trimmed)) BeDataType.Numeric
    else if (BeDataType.Boolean.isValidLiteral(trimmed)) BeDataType.Boolean
    else if (BeDataType.Date.isValidLiteral(trimmed)) BeDataType.Date
    else if (BeDataType.String.isValidLiteral(literalString)) BeDataType.String
    else BeDataType.Error
  }
}
