package interactionPlugins.blockEnvironment.programming

import contentmanagement.datastructures.tree.*
import contentmanagement.datastructures.tree.nodeImpl.*
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.*
import contentmanagement.model.language.AppLanguage.Python
import interactionPlugins.blockEnvironment.programming.BeProgram.*
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.call.*
import interactionPlugins.blockEnvironment.programming.blocks.call.parent.{BeBlockFunctionDefinition, BeBlockParent, BeMotionBlocks}
import interactionPlugins.blockEnvironment.programming.connection.*
import interactionPlugins.blockEnvironment.programming.rendering.*

type BeProgramLogicTree = Tree[NodeBasedTreePosition, BeBlockLogic]
type BeProgramDisplayTree = Tree[NodeBasedTreePosition, BeBlock]
type BeDimensionTree = Tree[NodeBasedTreePosition, Dimension[Double]]
type BePositionTree = Tree[NodeBasedTreePosition, Point[Double]]
type BeBoundsTree = Tree[NodeBasedTreePosition, Bounds[Double]]

type BeLogicContext[O] = TreeStructureAndExecutionContext[NodeBasedTreePosition, BeBlockLogic, O]
type BeBlockContext[O] = TreeStructureAndExecutionContext[NodeBasedTreePosition, BeBlock, O]

case class BeProgram(logicTree: BeProgramLogicTree) {

  val displayTree: BeProgramDisplayTree = BeProgramRendererHelper.calcDisplayTree(logicTree)

  def toPythonString: String = {
    val res = logicTree.mapWithContext[String](context => context.curValue.toCode(Python, context))
    res.getData(res.rootPosition.forChild(0)).get
  }


}


object BeProgram {

  /*
  private def enrichToParentContext[B <: BeBlock](structureContext: TreeStructureContext[NodeBasedTreePosition, B]): BeProgramParentContext = new BeProgramParentContext {

    override def block: BeBlockParent = structureContext.curValue.asInstanceOf[BeBlockParent]

    override def childrenBlocks: List[BeBlock] = structureContext.childrenValues.map(_.asInstanceOf[BeBlock])

    override def curPosition: NodeBasedTreePosition = structureContext.curPosition
  }

  def enrichToBeContext[B <: BeBlock, O](context: TreeStructureAndExecutionContext[NodeBasedTreePosition, B, O]): BeTreeContext[B, O] = new BeTreeContext[B, O] {

    override def block: B = context.curValue

    override def accessChildrenResults: List[O] = context.accessChildrenResults

    override def accessParentResult: Option[O] = context.accessParentResult

    override def accessOtherResult(otherPosition: TreeStructureContext[NodeBasedTreePosition, B]): O = context.accessOtherResult(otherPosition)

    override def curPosition: NodeBasedTreePosition = context.curPosition

    override def tree: Tree[NodeBasedTreePosition, B] = context.tree

    override def parentValue: Option[B] = context.parentValue

    override def curValue: B = context.curValue

    override def childrenValues: List[B] = context.childrenValues

    override def traversalInfoForParent: Option[TreeStructureContext[NodeBasedTreePosition, B]] = context.traversalInfoForParent

    override def traversalInfoForChildren: List[TreeStructureContext[NodeBasedTreePosition, B]] = context.traversalInfoForChildren
  }
*/

  trait BeProgramParentContext {
    def curPosition: NodeBasedTreePosition

    def block: BeBlockParent

    def childrenBlocks: List[BeBlock]
  }

  /*def testProgramAdding(): Unit = {
    val sample = miniProgram()
    val big = sampleProgram()

    val addPos = NodeBasedTreePosition(List(0,0,0))
    val res = sample.logicTree.addSubtree(addPos, big.logicTree)

    println("mini: " + sample)
    println("big: " + big)
    println("combined: " + res)
  }*/

  trait BeTreeContext[B <: BeBlock, O] extends TreeStructureAndExecutionContext[NodeBasedTreePosition, BeBlock, O] {
    def block: B

    def getChildrenWithRole(role: BeConnectionRole): List[TreeStructureContext[NodeBasedTreePosition, BeBlock]] = traversalInfoForChildren.filter(_.curValue.roleInParent == role)

    def getResultsOfRole(role: BeConnectionRole): List[O] = getChildrenWithRole(role).map(accessOtherResult)
  }

  def miniProgram(): BeProgram = {

    val starter = BeBlockFunctionDefinition.starterBlock()
    val forward = BeMotionBlocks.BeBlockForward(FunctionBody)

    var tree: Tree[NodeBasedTreePosition, BeBlockLogic] = NodeBasedTreeImpl.empty[BeBlockLogic]()
    tree = tree.addAsLastChild(tree.rootPosition, starter)

    tree = tree.addAsLastChild(tree.rootPosition.forChild(0), forward)
    tree = tree.addAsLastChild(tree.rootPosition.forChild(0), forward)
    tree = tree.addAsLastChild(tree.rootPosition.forChild(0).forChild(0), BeBlockValue(BeDataType.Numeric, FunctionParameter(BeDataType.Numeric), "This contains the value: " + 128 + ""))

    BeProgram(tree)
  }

  def sampleProgram(): BeProgram = {

    val starter = BeBlockFunctionDefinition.starterBlock()
    val forward = BeMotionBlocks.BeBlockForward(FunctionBody)

    var tree: Tree[NodeBasedTreePosition, BeBlockLogic] = NodeBasedTreeImpl.empty[BeBlockLogic]()
    tree = tree.addAsLastChild(tree.rootPosition, starter)


    0.until(10).foreach(curIter => {
      tree = tree.addAsLastChild(tree.rootPosition.forChild(0), forward)
      tree = tree.addAsLastChild(tree.rootPosition.forChild(0).forChild(curIter), BeBlockValue(BeDataType.Numeric, FunctionParameter(BeDataType.Numeric), "This contains the value: " + curIter + ""))
      if (curIter % 2 == 0) {
        tree = tree.addAsLastChild(tree.rootPosition.forChild(0).forChild(curIter), BeBlockValue(BeDataType.Numeric, FunctionParameter(BeDataType.Numeric), "Another one for 2"))
      }
      if (curIter % 3 == 0) {
        tree = tree.addAsLastChild(tree.rootPosition.forChild(0).forChild(curIter), BeBlockValue(BeDataType.Numeric, FunctionParameter(BeDataType.Numeric), "Another one with 3 "))
      }
    })

    val res = BeProgram(tree)
    res
  }


}
