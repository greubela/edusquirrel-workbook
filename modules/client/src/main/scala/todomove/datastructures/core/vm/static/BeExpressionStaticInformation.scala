package todomove.datastructures.core.vm.static

import todomove.datastructures.core.vm.code.BeDefineStructure
import todomove.datastructures.core.vm.code.tree.BeExpressionNode
import todomove.datastructures.core.vm.types.{BeDataType, BeDataValue, BeInfo, BeScope}

trait BeExpressionStaticInformation {

  def staticType: BeDataType = BeDataType.Unit

  def staticValue: Option[BeDataValue] = None
  
  def syntaxErrors: Seq[BeInfo] = List()

  def hasSideEffects: Boolean = false

  def getDefinitions: BeDefineStructure = new BeDefineStructure(){}
}
