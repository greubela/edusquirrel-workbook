package todomove.datastructures.core.vm.code.errors

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockComment
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.io.BeExpressionIO
import todomove.datastructures.core.vm.static.BeExpressionStaticInformation

case class BeSingleLineComment(commentStr: LanguageMap[HumanLanguage]) extends BeExpression {


  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {
      val comment = commentStr.getInLanguage(humanLanguage)
      if (comment.contains("\n")) comment.replaceAll("\n", " ") else comment
      programmingLanguage match {
        case Python => s"# $comment"
        case Java => s"// $comment"
        case Cpp => s"// $comment"
        case Lisp => s"; $comment"
        case JavaScript => s"// $comment"
        case BlockDisplay => s"// $comment"
        case _ => s"// $comment"
      }
    }

    override def createBlock(): BeBlock = BeBlockComment(BeSingleLineComment.this)
  }


}
