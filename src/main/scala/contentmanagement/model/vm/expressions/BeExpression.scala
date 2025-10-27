package contentmanagement.model.vm.expressions

import contentmanagement.datastructures.tree.nodeImpl.{NodeBasedTreeImpl, NodeBasedTreePosition}
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.BeExpressionTree
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

trait BeExpression {
  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String

  def hasSideEffects: Boolean
  def applySideEffects(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState

  def evaluateBlock(simulatorState: BeSimulatorState): BeUseValue


  def getSyntaxErrors: Seq[BeInfo]


  def canEvaluateTo: Set[BeDataType]

  def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock

  def recToTree(config: BeDisplayConfig, roleInParent: BeChildRole): BeExpressionTree = {
    val root: (BeChildRole, BeExpression) = (roleInParent, this)
    val childTree = getChildren.map(child => child._2.recToTree(config, child._1))

    var tree: BeExpressionTree = NodeBasedTreeImpl.empty[(BeChildRole, BeExpression)]()
    tree = tree.addAsLastChild(tree.rootPosition, root)
    childTree.reverse.zipWithIndex.foreach((curTree, curIndex) => if (curTree.size > 0) {
      tree = tree.addSubtreeAsLastChild(tree.rootPosition.forChild(0), curTree)
    })
    tree
  }

  def getChildren: List[(BeChildRole, BeExpression)]

}

object BeExpression {

  lazy val pass: BeExpression = new BeSequence(true, List())

  lazy val NoOp: BeExpression = new BeExpression {

    override def evaluateBlock(simulatorState: BeSimulatorState): BeUseValue = BeUseUnitValue

    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ""

    override def hasSideEffects: Boolean = false

    override def getSyntaxErrors: Seq[BeInfo] = List()

    override def applySideEffects(config: BeSimulatorConfig, simulatorState: BeSimulatorState): BeSimulatorState = simulatorState

    override def canEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

    override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = ???

    override def recToTree(config: BeDisplayConfig, roleInParent: BeChildRole): BeExpressionTree = {
      NodeBasedTreeImpl.empty[(BeChildRole, BeExpression)]()
    }
    
    override def getChildren: List[(BeChildRole, BeExpression)] = List()

  }

}