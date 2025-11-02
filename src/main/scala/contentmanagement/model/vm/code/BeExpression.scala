package contentmanagement.model.vm.code

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreeImpl
import contentmanagement.model.language.{AppLanguage, HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.code.errors.*
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference, BeExtensionPoint}
import contentmanagement.model.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeScope.GlobalScope
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.BeExpressionTree
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock


trait BeExpression {

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String

  def hasThisExpressionOrChildrenSideEffects: Boolean = hasThisExpressionSideEffects || hasChildrenSideEffects

  def hasChildrenSideEffects: Boolean = getChildren(false, GlobalScope()).flatMap {
    case BeExpressionReference(childPosition: BeChildPosition, expr: BeExpression) => Some(expr)
    case _ => None
  }.exists(_.hasThisExpressionOrChildrenSideEffects)

  def hasThisExpressionSideEffects: Boolean

  def getSyntaxErrorsOfThisStructure: Seq[BeInfo]

  def canEvaluateTo: BeDataType

  def getExecutor(simulatorConfig: BeSimulatorConfig, stateBeforeExecution: BeSimulatorState): BeExpressionExecutor =
    throw new NotImplementedError("Execution support is not implemented in the static VM model")

  def recToTree(withExtensions: Boolean, myPosition: BeChildPosition): BeExpressionTree = {
    val root: BeExpressionNode = BeExpressionReference(myPosition, this)

    val children: List[(BeExpressionNode, Option[BeExpressionTree])] = getChildren(withExtensions, myPosition.curScope).map(exprNode => exprNode match {
      case BeExpressionReference(childPosition: BeChildPosition, childExpr: BeExpression) => (exprNode, Some(childExpr.recToTree(withExtensions, childPosition)))
      case BeExtensionPoint(isRequired, childPosition, dataTypes) => (exprNode, None)
    })

    var tree: BeExpressionTree = NodeBasedTreeImpl.empty[BeExpressionNode]()
    tree = tree.addAsLastChild(tree.rootPosition, root)
    children.foreach((expr, opTree) => opTree match {
      case Some(expressionTree: BeExpressionTree) => tree = tree.addSubtreeAsLastChild(tree.rootPosition.forChild(0), expressionTree)
      case _ => tree = tree.addAsLastChild(tree.rootPosition.forChild(0), expr)
    })

    tree
  }

  def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = ???

  def createBlock(): BeBlock

  def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode]

  def stopExecutionBeforeThisBlock: Boolean = false
}

object BeExpression {

  val pass: BeExpression = BeSequence.optionalBody(List())

  /*
  private case object BePassExpression extends BeExpression {

    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
      programmingLanguage match {
        case AppLanguage.Python => "pass"
        case _ => ""
      }
    }

    override def hasThisExpressionSideEffects: Boolean = false

    override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()

    override def canEvaluateTo: BeDataType = BeDataType.Unit

    override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock =
      throw new NotImplementedError("Block rendering is not implemented for the pass expression")

    override def getChildren: List[(BeChildRole, BeExpression)] = List()
  }

  lazy val pass: BeExpression = BePassExpression

  lazy val NoOp: BeExpression = new BeExpression {

    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ""

    override def hasThisExpressionSideEffects: Boolean = false

    override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = List()

    override def canEvaluateTo: BeDataType = BeDataType.Unit

    override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock =
      throw new NotImplementedError("Block rendering is not implemented for the NoOp expression")

    override def recToTree(config: BeDisplayConfig, roleInParent: BeChildRole, myScope: BeScope): BeExpressionTree = {
      NodeBasedTreeImpl.empty[BeExpressionNode]()
    }

    def getChildren(withExtensions: Boolean): List[BeExpressionNode] = List()

    override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = this
  }*/

}