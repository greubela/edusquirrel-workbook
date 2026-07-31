package it.evadid.vm.code.errors

import it.evadid.core.datastructures.language.*
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.io.BeExpressionStructureInfo
import it.evadid.vm.naming.CodeRepresentationConfig
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.{BeDataType, BeInfo}

case class BeExpressionUnparsable(originalSource: String, message: String) extends BeExpression {

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def staticType: BeDataType = BeDataType.Error

    override def syntaxErrors: Seq[BeInfo] = List(BeInfo(LanguageMap.universalMap(message), BeInfo.SyntaxError.UnparsableBlock))
  }



}
