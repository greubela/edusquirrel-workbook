package todomove.datastructures.core.vm.code.errors

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockUnparsable
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.io.BeExpressionIO
import todomove.datastructures.core.vm.static.BeExpressionStaticInformation
import todomove.datastructures.core.vm.types.{BeDataType, BeInfo}

case class BeExpressionUnparsable(originalSource: String, message: String) extends BeExpression {

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def staticType: BeDataType = BeDataType.Error

    override def syntaxErrors: Seq[BeInfo] = List(BeInfo(LanguageMap.universalMap(message), BeInfo.SyntaxError.UnparsableBlock))
  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {
    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {
      if (skipUnparsable) "" else originalSource
    }

    override def toBlock(): BeBlock = BeBlockUnparsable(BeExpressionUnparsable.this)
  }


}
