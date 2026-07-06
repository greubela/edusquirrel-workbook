package it.evadid.vm.code.usage

import it.evadid.vm.code.BeExpression
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.io.BeExpressionIO
import it.evadid.vm.naming.CodeRepresentationConfig
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*

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
      import config.{humanLanguage, programmingLanguage}
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

