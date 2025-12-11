package contentmanagement.model.vm.code

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreeImpl
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference, BeExtensionPoint}
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeScope.GlobalScope
import interactionPlugins.blockEnvironment.programming.BeExpressionTree
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock


trait BeExpression {

  def expressionStaticInformation: BeExpressionStaticInformation =
    throw new NotImplementedError("Static Information is not implemented for " + getClass.getSimpleName)

  def expressionIO: BeExpressionIO =
    throw new NotImplementedError("Expression IO is not implemented for " + getClass.getSimpleName)

  def expressionExecutor(simulatorConfig: BeSimulatorConfig, stateBeforeExecution: BeSimulatorState): BeExpressionExecutor =
    throw new NotImplementedError("Execution support is not implemented for " + getClass.getSimpleName)


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

  def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode]

  def stopExecutionBeforeThisBlock: Boolean = false

  def staticInformationIncludingChildren: BeExpressionStaticInformation = new BeExpressionStaticInformation() {
    def staticType: BeDataType = expressionStaticInformation.staticType

    def staticValue: Option[BeDataValue] = expressionStaticInformation.staticValue

    def syntaxErrors: Seq[BeInfo] = expressionStaticInformation.syntaxErrors ++ getChildren(false, GlobalScope()).flatMap {
      case BeExpressionReference(childPosition: BeChildPosition, expr: BeExpression) => expr.staticInformationIncludingChildren.syntaxErrors
      case _ => None
    }

    def hasSideEffects: Boolean = expressionStaticInformation.hasSideEffects || getChildren(false, GlobalScope()).flatMap {
      case BeExpressionReference(childPosition: BeChildPosition, expr: BeExpression) => Some(expr)
      case _ => None
    }.exists(_.staticInformationIncludingChildren.hasSideEffects)
  }

}

object BeExpression {

  val pass: BeExpression = BeSequence.optionalBody(List())


}