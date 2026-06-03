package datastructures.core.vm.code.errors

import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.io.BeExpressionIO
import datastructures.core.vm.static.BeExpressionStaticInformation
import datastructures.core.vm.types.{BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockUnparsable

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
case class BeExpressionUnparsable(originalSource: String, message: String) extends BeExpression {

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def staticType: BeDataType = BeDataType.Error

    override def syntaxErrors: Seq[BeInfo] = List(BeInfo(LanguageMap.universalMap(message), BeInfo.SyntaxError.UnparsableBlock))
  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String =
      if skipUnparsable then "" else originalSource

    override def createBlock(): BeBlock = BeBlockUnparsable(BeExpressionUnparsable.this)
  }


}
