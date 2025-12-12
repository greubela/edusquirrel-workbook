package contentmanagement.model.vm.code.others

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.ExpressionInSequence
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock

case class BeReturn(value: Option[BeExpression]) extends BeExpression {

 /* override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val valueString = value.map(_.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", " "))
    val base = valueString match {
      case Some(text) if text.nonEmpty => s"return $text"
      case _ => "return"
    }
    programmingLanguage match {
      case Python => base
      case Java | JavaScript | Rust => if (base.endsWith(";")) base else base + ";"
      case _ => base
    }
  }

  override def hasThisExpressionSideEffects: Boolean = true

  override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] =
    value.map(_.getSyntaxErrorsOfThisStructure).getOrElse(Seq.empty)

  override def possibleStaticTypes: BeDataType = BeDataType.Unit

  override def createBlock(): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for return expressions")*/

  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] =
    value.map(expr => BeExpressionReference(BeChildPosition(ExpressionInSequence(0), parentScope), expr)).toList


  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val replacement = newChildren.collectFirst { case (ExpressionInSequence(_), expr) => expr }
    replacement.map(expr => copy(value = Some(expr))).getOrElse(this)
  }
}
