package datastructures.core.vm.code.errors

import datastructures.core.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.io.BeExpressionIO
import datastructures.core.vm.static.BeExpressionStaticInformation
import datastructures.core.vm.types.{BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockUnsupported

case class BeExpressionUnsupported(originalSource: String) extends BeExpression {

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def staticType: BeDataType = BeDataType.Error

    override def syntaxErrors: Seq[BeInfo] =
      List(BeInfo(LanguageMap.universalMap(s"Unknown Python structure: $originalSource"), BeInfo.SyntaxError.UnsupportedBlock))

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = originalSource

    override def createBlock(): BeBlock = BeBlockUnsupported(BeExpressionUnsupported.this)
  }


}
