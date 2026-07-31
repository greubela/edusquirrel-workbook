package it.evadid.vm.code.abstractions

import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreeImpl
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.tree.*
import it.evadid.vm.io.BeExpressionStructureInfo
import it.evadid.vm.simulation.*
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*
import it.evadid.vm.types.BeScope.GlobalScope

trait BeExpression {

  lazy val staticInformationExpression: BeExpressionStaticInformation = {
    println("[WARN] No static information implemented for " + getClass.getSimpleName)
    new BeExpressionStaticInformation() {}
  }

  lazy val structureInfo: BeExpressionStructureInfo[?] = ???

  def expressionExecutor(simulatorConfig: BeSimulatorConfig, stateBeforeExecution: BeSimulatorState): BeExpressionExecutor = {
    println("[WARN] Execution support is not implemented for " + getClass.getSimpleName + " (defaulting to NoOp)")
    new BeExpressionExecutor(simulatorConfig, stateBeforeExecution, this) {
      protected def childExpressionsToExecute(stateBeforeExecution: BeSimulatorState): Seq[BeExpression] = List()

      protected def applySideEffectsOfThisBlock(stateBeforeExecution: BeSimulatorState, childrenResults: Seq[(BeSimulatorState, BeDataValue)]): BeSimulatorState = stateBeforeExecution

      protected def executeThisBlockInSimulatorAndGetValue(stateBeforeExecution: BeSimulatorState, childrenResults: Seq[(BeSimulatorState, BeDataValue)]): (BeSimulatorState, BeDataValue) = (stateBeforeExecution, BeDataValueUnit())
    }
  }

  def recToTree(withExtensions: Boolean, myPosition: BeChildInfo): BeExpressionTree = {
    val root: BeExpressionNode = BeExpressionReference(myPosition, this)

    val children: Seq[(BeExpressionNode, Option[BeExpressionTree])] = structureInfo.getChildren(withExtensions, myPosition.myScope).map {
      case exprNode@BeExpressionReference(childPosition: BeChildInfo, childExpr: BeExpression) => (exprNode, Some(childExpr.recToTree(withExtensions, childPosition)))
      case exprNode@BeExtensionPoint(isRequired, childPosition, willBeUsedAsType) => (exprNode, None)
    }

    var tree: BeExpressionTree = NodeBasedTreeImpl.empty[BeExpressionNode]()
    tree = tree.addAsLastChild(tree.rootPosition, root)
    children.foreach((expr, opTree) => opTree match {
      case Some(expressionTree: BeExpressionTree) => tree = tree.addSubtreeAsLastChild(tree.rootPosition.forChild(0), expressionTree)
      case _ => tree = tree.addAsLastChild(tree.rootPosition.forChild(0), expr)
    })

    tree
  }


  def stopExecutionBeforeThisBlock: Boolean = false

  def staticInformationSubtree: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    private lazy val allExprChildren: List[BeExpression] = {
      recToTree(false, BeChildInfo(BeChildRole.NoRole, GlobalScope())).values.toList.collect { case BeExpressionReference(_, expr) => expr }
    }

    override def staticType: BeDataType = staticInformationExpression.staticType

    override def staticValue: Option[BeDataValue] = staticInformationExpression.staticValue

    override def syntaxErrors: Seq[BeInfo] = staticInformationExpression.syntaxErrors ++ allExprChildren.flatMap(_.staticInformationExpression.syntaxErrors)

    override def hasSideEffects: Boolean = staticInformationExpression.hasSideEffects || allExprChildren.exists(_.staticInformationExpression.hasSideEffects)

    /*
    override def getDefinitions: BeDefineStructure = new BeDefineStructure() {

      private lazy val myDefs: BeDefineStructure = staticInformationExpression.getDefinitions

      override lazy val definedClasses: List[BeDefineClass] = myDefs.definedClasses ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.definedClasses)

      override lazy val definedFunctions: List[BeDefineFunction] = myDefs.definedFunctions ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.definedFunctions)

      override lazy val definedVariables: List[BeDefineVariable] = myDefs.definedVariables ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.definedVariables)

      override lazy val allDefinedStructures: List[BeDefineStructure] = myDefs.allDefinedStructures ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.allDefinedStructures)
    }*/

  }

  def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = this


}

object BeExpression {

  val pass: BeSequence = BeSequence.optionalBody(List())

  //val noOp: BeSequence = BeSequence.optionalBody(List())

}
