package todomove.datastructures.core.vm.code.usage

import todomove.datastructures.core.vm.types.BeChildRole.ValueInAssignment

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.use.BeBlockAssignValue
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.code.defining.BeDefineVariable
import todomove.datastructures.core.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import todomove.datastructures.core.vm.io.BeExpressionIO
import todomove.datastructures.core.vm.static.BeExpressionStaticInformation
import todomove.datastructures.core.vm.types.{BeChildPosition, BeChildRole, BeInfo, BeScope}

case class BeAssignVariable(target: BeDefineVariable, value: BeExpression) extends BeExpression {

  //private val assignPossible: BeDataTypeAssigningPossible = target.variableType.canTakeValuesFrom(value.possibleStaticTypes)

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {

    override def syntaxErrors: Seq[BeInfo] = BeInfo.typeMismatchInfo("value " + value + " for assigning", target.staticInformationExpression.staticType, value.staticInformationExpression.staticType).toList

    override def hasSideEffects: Boolean = true
  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {

    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {

      def sanitizeRustName(name: String): String =
        if (name.nonEmpty && name.head.isUpper) name.head.toLower + name.tail else name

      val renderedValue = value.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable).replaceAll("\n", " ")
      val targetName = programmingLanguage match {
        case Python if renderedValue.trim.startsWith("lambda") =>
          target.name.getInLanguage(humanLanguage)
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


    override def toBlock(): BeBlock = BeBlockAssignValue(target, value)
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
