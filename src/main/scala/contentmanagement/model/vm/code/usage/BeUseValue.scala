package contentmanagement.model.vm.code.usage

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.data.BeBlockUseValue

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
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = value match {
      case BeDataValueLiteral(literalStr) if contextIfKnown.nonEmpty =>
        contextIfKnown.get.variableType.formatValueForDisplay(literalStr).getInLanguage(programmingLanguage)
      case BeDataValueLiteral(literalStr) => literalStr
      case reference: BeUseValueReference => reference.variable.name.getInLanguage(humanLanguage)
      case _ => value.displayAsString
    }

    override def createBlock(): BeBlock = BeBlockUseValue(BeUseValue.this)
  }

}

