package contentmanagement.model.vm.code

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreeImpl
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.BeExpressionTree
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

trait BeExpression {

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String

  def hasThisExpressionOrChildrenSideEffects: Boolean = hasThisExpressionSideEffects || getChildren.exists(_._2.hasThisExpressionOrChildrenSideEffects)

  def hasThisExpressionSideEffects: Boolean

  def getSyntaxErrors: Seq[BeInfo]

  def canEvaluateTo: Set[BeDataType]

  def getExecutor(simulatorConfig: BeSimulatorConfig, stateBeforeExecution: BeSimulatorState): BeExpressionExecutor = ???

  protected def changedScopeForChildren(parentScope: BeScope): BeScope = parentScope

  def recToTree(config: BeDisplayConfig, roleInParent: BeChildRole, myScope: BeScope): BeExpressionTree = {
    val root: (BeChildRole, BeExpression, BeScope) = (roleInParent, this, myScope)
    val newScope = changedScopeForChildren(myScope)
    val childTree = getChildren.map(child => child._2.recToTree(config, child._1, newScope))

    var tree: BeExpressionTree = NodeBasedTreeImpl.empty[(BeChildRole, BeExpression, BeScope)]()
    tree = tree.addAsLastChild(tree.rootPosition, root)
    childTree.reverse.zipWithIndex.foreach((curTree, curIndex) => if (curTree.size > 0) {
      tree = tree.addSubtreeAsLastChild(tree.rootPosition.forChild(0), curTree)
    })
    tree
  }

  def createBlock(config: BeDisplayConfig, childPos: BeChildPosition): BeBlock

  def getChildren: List[(BeChildRole, BeExpression)]

  def stopExecutionBeforeThisBlock: Boolean = false

}

object BeExpression {

  lazy val pass: BeExpression = new BeSequence(true, List())

  lazy val NoOp: BeExpression = new BeExpression {

    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ""

    override def hasThisExpressionSideEffects: Boolean = false

    override def getSyntaxErrors: Seq[BeInfo] = List()

    override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

    override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = ???

    override def recToTree(config: BeDisplayConfig, roleInParent: BeChildRole, myScope: BeScope): BeExpressionTree = {
      NodeBasedTreeImpl.empty[(BeChildRole, BeExpression, BeScope)]()
    }

    override def getChildren: List[(BeChildRole, BeExpression)] = List()

  }

}