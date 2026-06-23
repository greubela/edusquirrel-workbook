package it.evadid.workbook.vm.code.usage


import it.evadid.workbook.vm.naming.CodeRepresentationConfig
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.code.defining.BeDefineVariable
import it.evadid.workbook.vm.io.BeExpressionIO
import it.evadid.workbook.vm.static.BeExpressionStaticInformation
import it.evadid.workbook.vm.types.{BeDataType, BeDataValue, BeDataValueLiteral, BeUseValueReference}

/* necessary to distinguish between "variable is used in a reading context for assigning" or "variable is used in a reading context as parameter"...
 because the VALUE and VARIABLE might be the same... but the USAGE is not (for comparison reasons)
 also... necessary to get from the definevariable to the usevariable
enables to not implement everything in the value (which would be problematic for comparison reasons - 100 and 100 are the same, 100 at this position as parameter and 100 at that position as var def are not the same
*/

case class BeUseValue(value: BeDataValue, contextIfKnown: Option[BeDefineVariable]) extends BeExpression {

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def staticType: BeDataType = value.currentType

    override def staticValue: Option[BeDataValue] = Some(value)

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def toStringWithConfig(config: CodeRepresentationConfig): String = {
      import config.{programmingLanguage, humanLanguage, skipUnparsable}
      value match {
      case BeDataValueLiteral(literalStr) if contextIfKnown.nonEmpty =>
        contextIfKnown.get.variableType.formatValueForDisplay(literalStr).getInLanguage(programmingLanguage)
      case BeDataValueLiteral(literalStr) => literalStr
      case reference: BeUseValueReference => reference.variable.name.getNameIn(humanLanguage, config.namingStyle)
      case _ => value.displayAsString
      }
    }

  }

}

