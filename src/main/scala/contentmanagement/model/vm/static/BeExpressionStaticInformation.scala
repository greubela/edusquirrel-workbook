package contentmanagement.model.vm.static

import contentmanagement.model.vm.code.BeDefineStructure
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.{BeDataType, BeDataValue, BeInfo, BeScope}

trait BeExpressionStaticInformation {

  def staticType: BeDataType = BeDataType.Unit

  def staticValue: Option[BeDataValue] = None
  
  def syntaxErrors: Seq[BeInfo] = List()

  def hasSideEffects: Boolean = false

  def getDefinitions: BeDefineStructure = new BeDefineStructure(){}
}
