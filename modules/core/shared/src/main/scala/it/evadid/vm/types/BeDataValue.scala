package it.evadid.vm.types

import it.evadid.core.datastructures.language.AppLanguage

import scala.util.Try
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.defining.BeDefineVariable
import upickle.ReadWriter

sealed trait BeDataValue derives ReadWriter{

  def currentType: BeDataType
  
}


@upickle.implicits.key("BeDataValueUnit")
case class BeDataValueUnit() extends BeDataValue derives upickle.default.ReadWriter {

  val displayAsString: String = ""

  val currentType: BeDataType = BeDataType.Unit
}

@upickle.implicits.key("BeUseValueReference")
case class BeUseValueReference(variable: BeDefineVariable) extends BeDataValue derives upickle.default.ReadWriter {

  override def currentType: BeDataType = variable.variableType

}

@upickle.implicits.key("BeDataValueLiteral")
case class BeDataValueLiteral(literalString: String) extends BeDataValue derives upickle.default.ReadWriter {
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
