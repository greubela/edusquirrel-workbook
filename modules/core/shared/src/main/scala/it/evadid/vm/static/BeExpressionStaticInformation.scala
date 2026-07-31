package it.evadid.vm.static

import it.evadid.vm.code.abstractions.BeDefineStructure
import it.evadid.vm.types.{BeDataType, BeDataValue, BeInfo}

trait BeExpressionStaticInformation {

  def staticType: BeDataType = BeDataType.Unit

  def staticValue: Option[BeDataValue] = None

  def syntaxErrors: Seq[BeInfo] = List()

  def hasSideEffects: Boolean = false

  def getDefinitions: BeDefineStructure = new BeDefineStructure() {}
}

object BeExpressionStaticInformation {

  val empty: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

  }

}
