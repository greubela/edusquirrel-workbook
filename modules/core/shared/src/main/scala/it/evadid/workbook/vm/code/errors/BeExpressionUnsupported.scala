package it.evadid.workbook.vm.code.errors

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.io.BeExpressionIO
import it.evadid.workbook.vm.static.BeExpressionStaticInformation
import it.evadid.workbook.vm.types.{BeDataType, BeInfo}

case class BeExpressionUnsupported(originalSource: String) extends BeExpression {

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def staticType: BeDataType = BeDataType.Error

    override def syntaxErrors: Seq[BeInfo] =
      List(BeInfo(LanguageMap.universalMap(s"Unknown Python structure: $originalSource"), BeInfo.SyntaxError.UnsupportedBlock))

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {
    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = true): String ={
      if(skipUnparsable){
        println(s"[WARN] BeExpressionUnsupported::getInLanguage with flag 'skipUnparsable' set to true. Still rendering because '$originalSource' is unsupported, not unparsable!")
      }
      originalSource
    }

  }


}
