package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeDataValue, BeDataValueUnit, BeInfo, BeScope}
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import util.CodeStringBuilder
import contentmanagement.model.vm.code.tree.*
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.BeChildRole.ConditionInControlStructure
import contentmanagement.model.vm.types.BeScope.InSequenceScope
import interactionPlugins.blockEnvironment.programming.blockdisplay.control.BeBlockWhile

case class BeWhile(
                    condition: BeSequence,
                    body: BeSequence)
  extends BeControlStructure {

  override def allPossibleBodies: List[BeExpression] = List(body)

  

  override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = {
    List(
      BeExpressionReference(BeChildPosition(ConditionInControlStructure, InSequenceScope(condition, myScope)), condition),
      BeExpressionReference(BeChildPosition(BeChildRole.BodySequence(0), InSequenceScope(body, myScope)), body),
    )
  }



  override def expressionStaticInformation: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def staticType: BeDataType = BeDataType.Unit

    override def staticValue: Option[BeDataValue] = Some(BeDataValueUnit())

    override def syntaxErrors: Seq[BeInfo] = BeInfo.typeMismatchInfo("while condition", BeDataType.Boolean, condition.expressionStaticInformation.staticType).toList

    override def hasSideEffects: Boolean = false

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
      val conditionString = condition.expressionIO.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", "")
      val bodyString = body.expressionIO.getInLanguage(programmingLanguage, humanLanguage)
      programmingLanguage match {
        case Python =>
          CodeStringBuilder().appendNextLine(s"while $conditionString:")
            .changeIntLevel(1)
            .appendAsLines(bodyString)
            .toString
        case Java =>
          CodeStringBuilder().appendNextLine(s"while($conditionString){")
            .changeIntLevel(1)
            .appendAsLines(bodyString)
            .changeIntLevel(-1)
            .appendNextLine("}")
            .toString
        case JavaScript =>
          CodeStringBuilder().appendNextLine(s"while ($conditionString) {")
            .changeIntLevel(1)
            .appendAsLines(bodyString)
            .changeIntLevel(-1)
            .appendNextLine("}")
            .toString
        case Rust =>
          CodeStringBuilder().appendNextLine(s"while $conditionString {")
            .changeIntLevel(1)
            .appendAsLines(bodyString)
            .changeIntLevel(-1)
            .appendNextLine("}")
            .toString
        case Lisp =>
          CodeStringBuilder("(loop while " + conditionString)
            .changeIntLevel(1)
            .appendNextLine("do (progn")
            .changeIntLevel(1)
            .appendAsLines(bodyString)
            .changeIntLevel(-1)
            .appendNextLine(")")
            .changeIntLevel(-1)
            .appendNextLine(")")
            .toString
        case _ =>
          CodeStringBuilder().appendNextLine(s"WHILE(")
            .changeIntLevel(1)
            .appendNextLine(conditionString)
            .appendAsLines(bodyString)
            .changeIntLevel(-1)
            .appendNextLine(")")
            .toString
      }
    }

    override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
      val newCondition = newChildren.collectFirst {
        case (ConditionInControlStructure, seq: BeSequence) => seq
      }.getOrElse(condition)

      val newBody = newChildren.collectFirst {
        case (BeChildRole.BodySequence(0), seq: BeSequence) => seq
      }.getOrElse(body)

      copy(condition = newCondition, body = newBody)
    }

    override def createBlock(): BeBlock = BeBlockWhile(BeWhile.this)
  }

  override def expressionExecutor(simulatorConfig: BeSimulatorConfig, stateBeforeExecution: BeSimulatorState): BeExpressionExecutor = new BeExpressionExecutor(simulatorConfig, stateBeforeExecution, this) {
    override protected def childExpressionsToExecute(stateBeforeExecution: BeSimulatorState): List[BeExpression] = ???

    override protected def applySideEffectsOfThisBlock(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): BeSimulatorState = ???

    override protected def executeThisBlockInSimulatorAndGetValue(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): (BeSimulatorState, BeDataValue) = ???
  }





}
