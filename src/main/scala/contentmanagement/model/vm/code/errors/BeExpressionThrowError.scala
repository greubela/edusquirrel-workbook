package contentmanagement.model.vm.code.errors

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

case class BeExpressionThrowError(errorExpression: Option[BeExpression]) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val valueString = errorExpression.map(_.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", " "))
    programmingLanguage match {
      case Python => valueString.map(expr => s"raise $expr").getOrElse("raise")
      case Java => valueString.map(expr => s"throw new RuntimeException($expr);").getOrElse("throw new RuntimeException();")
      case JavaScript => valueString.map(expr => s"throw new Error($expr);").getOrElse("throw new Error();")
      case Rust => valueString.map(expr => s"panic!($expr);").getOrElse("panic!();")
      case Lisp => valueString.map(expr => s"(error $expr)").getOrElse("(error)")
      case _ => valueString.getOrElse("THROW")
    }
  }

  override def hasThisExpressionSideEffects: Boolean = true

  override def getSyntaxErrors: Seq[BeInfo] = errorExpression.map(_.getSyntaxErrors).getOrElse(Seq.empty)

  override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for throw expressions")

  override def getChildren: List[(BeChildRole, BeExpression)] =
    errorExpression.map(expr => (BeChildRole.ExpressionInBody(0), expr)).toList
}
