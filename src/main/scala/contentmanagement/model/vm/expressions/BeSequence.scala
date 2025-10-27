package contentmanagement.model.vm.expressions

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockSequence
import util.CodeStringBuilder

case class BeSequence(shouldEvaluateToUnit: Boolean, body: List[BeExpression]) extends BeExpression {

  def hasSideEffects: Boolean = body.exists(_.hasSideEffects)

  def getSyntaxErrors: Seq[BeInfo] = List() // whether it may be empty must be checked by the parent

  def applySideEffects(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = {
    var currentState = simulatorState
    for (curExpression <- body) {
      currentState = curExpression.applySideEffects(config, currentState)
    }
    currentState
  }

  def canEvaluateTo: Set[BeDataType] = if (shouldEvaluateToUnit || body.isEmpty) Set(BeDataType.Unit) else body.last.canEvaluateTo

  override  def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = BeBlockSequence(this, parentPos)

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = body.map(_.getInLanguage(programmingLanguage, humanLanguage)).mkString("\n")

  override def getChildren: List[(BeChildRole, BeExpression)] =
    body.zipWithIndex.map((curExpr, curIndex) => (BeChildRole.ExpressionInBody(curIndex), curExpr))

  override val toString: String = {
    var res = CodeStringBuilder(s"BeSequence(")
      .changeIntLevel(2)
      .appendNextLine(s"//always unit=$shouldEvaluateToUnit")
      .changeIntLevel(-1)
    if (body.nonEmpty)       res = res.changeForEach(body, (old, curExpr) => old.appendAsLines(curExpr.toString))
    else       res = res.appendNextLine("[no body]")
    res.changeIntLevel(-1)
      .appendNextLine(")").toString
  }

  override def evaluateBlock(simulatorState: BeSimulatorState): BeUseValue = BeUseUnitValue

}

object BeSequence {

  def optionalUnitBody(body: List[BeExpression]) = BeSequence(true, body)


}