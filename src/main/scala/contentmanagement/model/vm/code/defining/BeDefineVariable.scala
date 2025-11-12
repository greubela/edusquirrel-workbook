package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.AppLanguage.{Java, Python}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.data.*

case class BeDefineVariable(
    name: LanguageMap[HumanLanguage],
    val variableType: BeDataType,
    hasExplicitTypeHint: Boolean = false
) extends BeDefineStructure {

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val baseName = name.getInLanguage(humanLanguage)
    val typeHint = variableType.formatTypeForDisplay.getInLanguage(programmingLanguage).trim
    val shouldInclude = hasExplicitTypeHint && typeHint.nonEmpty
    programmingLanguage match {
      case Python =>
        if (shouldInclude) s"$baseName: $typeHint" else baseName
      case Java =>
        val javaType = if (typeHint.nonEmpty) typeHint else "Object"
        if (shouldInclude) s"$javaType $baseName" else baseName
      case _ => baseName
    }
  }

  override def hasThisExpressionSideEffects: Boolean = true

  override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()

  override def createBlock(): BeBlock =
    BeBlockDefineVariable(this)

  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = List()

  override val toString: String = "BeDefineVariable(" + name.toString + ": " + canEvaluateTo + ")"

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = this

  override def canEvaluateTo: BeDataType = variableType

}


/*
trait BeValueDefinition {

  def currentValue(simulator: BeSimulatorState): Option[String]

  def createBlock(displayConfig: BeDisplayConfig, roleInParent: BeChildRole): BeBlock
}
*/