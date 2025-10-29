package contentmanagement.model.vm.code.others

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

case class BeReturn(value: Option[BeExpression]) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val valueString = value.map(_.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", " "))
    programmingLanguage match {
      case Python => valueString.map(expr => s"return $expr").getOrElse("return")
      case Java => valueString.map(expr => s"return $expr;").getOrElse("return;")
      case JavaScript => valueString.map(expr => s"return $expr;").getOrElse("return;")
      case Rust => valueString.map(expr => s"return $expr;").getOrElse("return;")
      case Lisp => valueString.map(expr => s"(return-from nil $expr)").getOrElse("(return-from nil)")
      case _ => valueString.getOrElse("RETURN")
    }
  }

  override def hasThisExpressionSideEffects: Boolean = true

  override def getSyntaxErrors: Seq[BeInfo] = value.map(_.getSyntaxErrors).getOrElse(Seq.empty)

  override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for return expressions")

  override def getChildren: List[(BeChildRole, BeExpression)] =
    value.map(expr => (BeChildRole.ExpressionInBody(0), expr)).toList
}
