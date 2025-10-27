package contentmanagement.model.vm.expressions

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

import scala.collection.mutable.ListBuffer

case class BeExpressionIfElse(
    conditionSource: String,
    ifBody: List[BeExpression],
    elseBody: List[BeExpression]
) extends BeExpression {

  private val conditionExpression = BeExpressionUnkown(conditionSource)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val conditionStr = conditionSource
    val ifLines = renderBody(ifBody, programmingLanguage, humanLanguage)
    val elseLines = renderBody(elseBody, programmingLanguage, humanLanguage)
    val elsePart = if (elseLines.nonEmpty) s"\nelse:\n${elseLines.mkString("\n")}" else ""
    val ifPart = if (ifLines.nonEmpty) ifLines.mkString("\n") else "    pass"
    s"if $conditionStr:\n$ifPart$elsePart"
  }

  override def hasSideEffects: Boolean = ifBody.exists(_.hasSideEffects) || elseBody.exists(_.hasSideEffects)

  override def getSyntaxErrors: Seq[BeInfo] =
    ifBody.flatMap(_.getSyntaxErrors) ++ elseBody.flatMap(_.getSyntaxErrors)

  override def execute(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = simulatorState

  override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

  override def createBlock(config: BeDisplayConfig, roleInParent: BeChildRole): BeBlock = ???

  override def getChildren: List[(BeChildRole, BeExpression)] = {
    val children = ListBuffer.empty[(BeChildRole, BeExpression)]
    children += ((BeChildRole.ExpressionInBody(0), conditionExpression))
    if (ifBody.nonEmpty) {
      children += ((BeChildRole.BodySequence(), BeSequence(ifBody, mayBeEmpty = true, Some(Set(BeDataType.Unit)))))
    }
    if (elseBody.nonEmpty) {
      children += ((BeChildRole.BodySequence(), BeSequence(elseBody, mayBeEmpty = true, Some(Set(BeDataType.Unit)))))
    }
    children.toList
  }

  private def renderBody(
      expressions: List[BeExpression],
      programmingLanguage: ProgrammingLanguage,
      humanLanguage: HumanLanguage
  ): List[String] = {
    if (expressions.isEmpty) List()
    else {
      expressions.flatMap { expr =>
        val rendered = expr.getInLanguage(programmingLanguage, humanLanguage)
        if (rendered.isEmpty) List("    ")
        else rendered.linesIterator.map(line => s"    ${line}").toList
      }
    }
  }
}
