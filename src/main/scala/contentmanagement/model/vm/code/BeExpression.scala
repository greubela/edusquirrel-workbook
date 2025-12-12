package contentmanagement.model.vm.code

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreeImpl
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference, BeExtensionPoint}
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeScope.GlobalScope
import interactionPlugins.blockEnvironment.programming.BeExpressionTree
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock

import scala.collection.mutable


trait BeExpression {

  def staticInformationExpression: BeExpressionStaticInformation = {
    println("[WARN] No static information implemented for " + getClass.getSimpleName)
    new BeExpressionStaticInformation() {}
  }

  def expressionIO: BeExpressionIO = {
    println("[WARN] No expression io implemented for " + getClass.getSimpleName)
    new BeExpressionIO() {}
  }

  def expressionExecutor(simulatorConfig: BeSimulatorConfig, stateBeforeExecution: BeSimulatorState): BeExpressionExecutor = {
    println("[WARN] Execution support is not implemented for " + getClass.getSimpleName + " (defaulting to NoOp)")
    new BeExpressionExecutor(simulatorConfig, stateBeforeExecution, this) {
      protected def childExpressionsToExecute(stateBeforeExecution: BeSimulatorState): List[BeExpression] = List()

      protected def applySideEffectsOfThisBlock(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): BeSimulatorState = stateBeforeExecution

      protected def executeThisBlockInSimulatorAndGetValue(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): (BeSimulatorState, BeDataValue) = (stateBeforeExecution, BeDataValueUnit())
    }
  }


  def recToTree(withExtensions: Boolean, myPosition: BeChildPosition): BeExpressionTree = {
    val root: BeExpressionNode = BeExpressionReference(myPosition, this)

    val children: List[(BeExpressionNode, Option[BeExpressionTree])] = getChildren(withExtensions, myPosition.curScope).map(exprNode => exprNode match {
      case BeExpressionReference(childPosition: BeChildPosition, childExpr: BeExpression) => (exprNode, Some(childExpr.recToTree(withExtensions, childPosition)))
      case BeExtensionPoint(isRequired, childPosition, willBeUsedAsType) => (exprNode, None)
    })

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
      recToTree(false, BeChildPosition(BeChildRole.NoRole, GlobalScope())).values.toList.collect { case BeExpressionReference(_, expr) => expr }
    }

    override def staticType: BeDataType = staticInformationExpression.staticType

    override def staticValue: Option[BeDataValue] = staticInformationExpression.staticValue

    override def syntaxErrors: Seq[BeInfo] = staticInformationExpression.syntaxErrors ++ allExprChildren.flatMap(_.staticInformationExpression.syntaxErrors)

    override def hasSideEffects: Boolean = staticInformationExpression.hasSideEffects || allExprChildren.exists(_.staticInformationExpression.hasSideEffects)

    override def getDefinitions: BeDefineStructure = new BeDefineStructure() {

      private lazy val myDefs: BeDefineStructure = staticInformationExpression.getDefinitions

      override lazy val definedClasses: List[BeDefineClass] = myDefs.definedClasses ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.definedClasses)

      override lazy val definedFunctions: List[BeDefineFunction] = myDefs.definedFunctions ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.definedFunctions)

      override lazy val definedVariables: List[BeDefineVariable] = myDefs.definedVariables ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.definedVariables)

      override lazy val allDefinedStructures: List[BeDefineStructure] = myDefs.allDefinedStructures ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.allDefinedStructures)
    }

  }

  def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = List()

  def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = this

}

object BeExpression {

  val pass: BeSequence = BeSequence.optionalBody(List())

  //val noOp: BeSequence = BeSequence.optionalBody(List())

}