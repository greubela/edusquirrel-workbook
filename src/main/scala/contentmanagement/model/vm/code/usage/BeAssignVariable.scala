package contentmanagement.model.vm.code.usage

import contentmanagement.model.language.AppLanguage.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.types.BeInfo.WarningType
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.ValueInAssignment
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.use.BeBlockAssignValue

case class BeAssignVariable(target: BeDefineVariable, value: BeExpression) extends BeExpression {

  private val assignPossible: BeDataTypeAssigningPossible = target.variableType.canTakeValuesFrom(value.canEvaluateTo)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val renderedValue = value.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", " ")
    val targetName = programmingLanguage match {
      case Python if renderedValue.trim.startsWith("lambda") =>
        target.name.getInLanguage(humanLanguage)
      case _ =>
        target.getInLanguage(programmingLanguage, humanLanguage)
    }

    programmingLanguage match {
      case Python => s"$targetName = $renderedValue"
      case Java => s"$targetName = $renderedValue;"
      case JavaScript => s"$targetName = $renderedValue;"
      case Rust => s"${sanitizeRustName(targetName)} = $renderedValue;"
      case Lisp => s"(setf ${targetName.toLowerCase} $renderedValue)"
      case _ => s"$targetName := $renderedValue"
    }
  }

  override def hasThisExpressionSideEffects: Boolean = true


  override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] =
    BeInfo.typeMismatchInfo("value " + value + " for assigning", target.canEvaluateTo, value.canEvaluateTo).toList


  override def canEvaluateTo: BeDataType = BeDataType.Unit

  override def createBlock(): BeBlock =
    BeBlockAssignValue(target, value)

  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = List(
    BeExpressionReference(BeChildPosition(ValueInAssignment, parentScope), value)
  ) 

  /*
  
    parameterWithValues.zipWithIndex.filter(_._1._2.nonEmpty).map( (tup, index) => (tup._1, tup._2.get, index) ).map( (parVar, parVal, parNr) => {
      BeExpressionReference(BeChildPosition(FunctionParameter(parNr), parentScope), parVal)
    })
   */

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val replacement = newChildren.collectFirst {
      case (BeChildRole.ValueForVariable(variable), expr) if variable == target => expr
      case (BeChildRole.ExpressionInSequence(_), expr) => expr
    }

    replacement.map(expr => copy(value = expr)).getOrElse(this)
  }


  private def sanitizeRustName(name: String): String =
    if (name.nonEmpty && name.head.isUpper) name.head.toLower + name.tail else name
}
