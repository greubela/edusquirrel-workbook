package contentmanagement.model.vm.static

import contentmanagement.model.vm.types.{BeDataType, BeDataValue, BeInfo}

trait BeExpressionStaticInformation {
  
  def staticType: BeDataType

  def staticValue: Option[BeDataValue]
  
  def syntaxErrors: Seq[BeInfo]

  def hasSideEffects: Boolean

}
