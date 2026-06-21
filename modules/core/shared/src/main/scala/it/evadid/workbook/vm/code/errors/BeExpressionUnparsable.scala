package it.evadid.workbook.vm.code.errors

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.io.BeExpressionIO
import it.evadid.workbook.vm.static.BeExpressionStaticInformation
import it.evadid.workbook.vm.types.{BeDataType, BeInfo}

case class BeExpressionUnparsable(originalSource: String, message: String) extends BeExpression {

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def staticType: BeDataType = BeDataType.Error

    override def syntaxErrors: Seq[BeInfo] = List(BeInfo(LanguageMap.universalMap(message), BeInfo.SyntaxError.UnparsableBlock))
  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {
    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {
      if (skipUnparsable) "" else originalSource
    }

  }


}
