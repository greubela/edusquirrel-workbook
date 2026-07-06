package it.evadid.vm.code.usage

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.BeExpression
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.io.BeExpressionIO
import it.evadid.vm.naming.CodeRepresentationConfig
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.BeChildRole.ValueInAssignment
import it.evadid.vm.types.*

case class BeAssignVariable(target: BeDefineVariable, value: BeExpression) extends BeExpression {

  //private val assignPossible: BeDataTypeAssigningPossible = target.variableType.canTakeValuesFrom(value.possibleStaticTypes)

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {

    override def syntaxErrors: Seq[BeInfo] = BeInfo.typeMismatchInfo("value " + value + " for assigning", target.staticInformationExpression.staticType, value.staticInformationExpression.staticType).toList

    override def hasSideEffects: Boolean = true
  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {

    override def toStringWithConfig(config: CodeRepresentationConfig): String = {
      import config.*

      def sanitizeRustName(name: String): String =
        if (name.nonEmpty && name.head.isUpper) s"${name.head.toLower}${name.tail}" else name

      val renderedValue = value.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable).replaceAll("\n", " ")
      val targetName = programmingLanguage match {
        case Python if renderedValue.trim.startsWith("lambda") =>
          target.name.getNameIn(humanLanguage, config.namingStyle)
        case _ =>
          target.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)
      }

      programmingLanguage match {
        case Python => s"$targetName = $renderedValue"
        case Java => s"$targetName = $renderedValue;"
        case JavaScript => s"$targetName = $renderedValue;"
        case Cpp => s"$targetName = $renderedValue;"
        case Rust => s"${sanitizeRustName(targetName)} = $renderedValue;"
        case Lisp => s"(setf ${targetName.toLowerCase} $renderedValue)"
        case _ => s"$targetName := $renderedValue"
      }
    }


  }

  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = List(
    BeExpressionReference(BeChildPosition(ValueInAssignment, parentScope), value)
  )

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val replacement = newChildren.collectFirst {
      case (BeChildRole.ValueForVariable(variable), expr) if variable == target => expr
      case (BeChildRole.ExpressionInSequence(_), expr) => expr
    }

    replacement.map(expr => copy(value = expr)).getOrElse(BeAssignVariable.this)
  }

}
