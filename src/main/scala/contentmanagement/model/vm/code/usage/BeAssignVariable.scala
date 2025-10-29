package contentmanagement.model.vm.code.usage

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

case class BeAssignVariable(target: BeDefineVariable, value: BeExpression) extends BeExpression {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val targetName = target.name.getInLanguage(humanLanguage)
    val valueString = value.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", " ")
    programmingLanguage match {
      case Python => s"$targetName = $valueString"
      case Java => s"$targetName = $valueString;"
      case JavaScript => s"$targetName = $valueString;"
      case Rust => s"${sanitizeRustName(targetName)} = $valueString;"
      case Lisp => s"(setf ${targetName.toLowerCase} $valueString)"
      case _ => s"$targetName := $valueString"
    }
  }

  override def hasThisExpressionSideEffects: Boolean = true

  override def getSyntaxErrors: Seq[BeInfo] = value.getSyntaxErrors

  override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for assignments")

  override def getChildren: List[(BeChildRole, BeExpression)] =
    List((BeChildRole.ValueForVariable(target), value))

  private def sanitizeRustName(name: String): String =
    if (name.nonEmpty && name.head.isUpper) name.head.toLower + name.tail else name
}
