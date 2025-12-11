package contentmanagement.model.vm.code.errors

import contentmanagement.model.language.AppLanguage.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.{BeChildRole, BeDataType, BeDataValue, BeDataValueUnit, BeInfo, BeScope}
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockComment

case class BeSingleLineComment(commentStr: LanguageMap[HumanLanguage]) extends BeExpression {


  override def expressionStaticInformation: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    def staticType: BeDataType = BeDataType.Unit

    def staticValue: Option[BeDataValue] = Some(BeDataValueUnit())

    def syntaxErrors: Seq[BeInfo] = List()

    def hasSideEffects: Boolean = false
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

    def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = BeSingleLineComment.this

    def createBlock(): BeBlock = BeBlockComment(BeSingleLineComment.this)
  }
  
  
  


  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = List()
}
