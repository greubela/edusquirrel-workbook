package it.evadid.vm.types

import it.evadid.core.datastructures.language.AppLanguage
import scala.util.Try

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.defining.BeDefineVariable

trait BeDataValue {

  def currentType: BeDataType

  def displayAsString: String
  
}


case class BeDataValueUnit() extends BeDataValue {

  val displayAsString: String = ""

  val currentType: BeDataType = BeDataType.Unit
}

case class BeUseValueReference(variable: BeDefineVariable) extends BeDataValue {

  override def currentType: BeDataType = variable.variableType

  override def displayAsString: String = variable.name.universalInterpretation()
}

case class BeDataValueLiteral(literalString: String) extends BeDataValue {
  def displayAsString: String = literalString

  override val currentType: BeDataType = {
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
