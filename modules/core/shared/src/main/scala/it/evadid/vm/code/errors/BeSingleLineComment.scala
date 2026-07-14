package it.evadid.vm.code.errors

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.BeExpression
import it.evadid.vm.io.BeExpressionIO
import it.evadid.vm.naming.CodeRepresentationConfig
import it.evadid.vm.static.BeExpressionStaticInformation

case class BeSingleLineComment(commentStr: LanguageMap[HumanLanguage]) extends BeExpression {


  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {
    override def toStringWithConfig(config: CodeRepresentationConfig): String = {
      val comment = commentStr.getInLanguage(config.humanLanguage)
      if (comment.contains("\n")) comment.replaceAll("\n", " ") else comment
      config.programmingLanguage match {
        case Python => s"# $comment"
        case Java => s"// $comment"
        case Cpp => s"// $comment"
        case Lisp => s"; $comment"
        case JavaScript => s"// $comment"
        case BlockDisplay => s"// $comment"
        case _ => s"// $comment"
      }
    }

  }


}
