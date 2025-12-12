package contentmanagement.model.vm.code.errors

import contentmanagement.model.language.AppLanguage.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockComment

case class BeSingleLineComment(commentStr: LanguageMap[HumanLanguage]) extends BeExpression {


  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
      val comment = commentStr.getInLanguage(humanLanguage)
      if (comment.contains("\n")) comment.replaceAll("\n", " ") else comment
      programmingLanguage match {
        case Python => s"# $comment"
        case Java => s"// $comment"
        case Lisp => s"; $comment"
        case JavaScript => s"// $comment"
        case BlockDisplay => s"// $comment"
        case _ => s"// $comment"
      }
    }

    override def createBlock(): BeBlock = BeBlockComment(BeSingleLineComment.this)
  }


}
