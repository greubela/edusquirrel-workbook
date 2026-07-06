package it.evadid.vm.static

import it.evadid.vm.code.BeDefineStructure
import it.evadid.vm.code.tree.BeExpressionNode
import it.evadid.vm.types.{BeDataType, BeDataValue, BeInfo, BeScope}

trait BeExpressionStaticInformation {

  def staticType: BeDataType = BeDataType.Unit

  def staticValue: Option[BeDataValue] = None
  
  def syntaxErrors: Seq[BeInfo] = List()

  def hasSideEffects: Boolean = false

  def getDefinitions: BeDefineStructure = new BeDefineStructure(){}
}
