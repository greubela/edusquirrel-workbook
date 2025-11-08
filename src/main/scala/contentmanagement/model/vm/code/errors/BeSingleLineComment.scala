package contentmanagement.model.vm.code.errors

import contentmanagement.model.language.AppLanguage.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.{BeChildRole, BeDataType, BeInfo, BeScope}
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockComment

case class BeSingleLineComment(commentStr: LanguageMap[HumanLanguage]) extends BeExpression {

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

  override def hasThisExpressionSideEffects: Boolean = false

  override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()

  override def canEvaluateTo: BeDataType = BeDataType.Unit

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = this

  override def createBlock(): BeBlock = BeBlockComment(this)

  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = List()
}
