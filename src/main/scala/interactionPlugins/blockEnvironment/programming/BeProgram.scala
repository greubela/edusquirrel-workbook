package interactionPlugins.blockEnvironment.programming

import contentmanagement.datastructures.tree.*
import contentmanagement.datastructures.tree.nodeImpl.*
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.*
import interactionPlugins.blockEnvironment.programming.BeProgram.{BeProgramTreeContext, enrichContext}
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.BeMotionBlocks.{BeBlockForward, MotionBlock}
import interactionPlugins.blockEnvironment.programming.connection.*

type BeProgramTree = Tree[BeBlock, NodeBasedTreePosition]
type BeDimensionTree = Tree[Dimension[Double], NodeBasedTreePosition]
type BePositionTree = Tree[Point[Double], NodeBasedTreePosition]
type BeBoundsTree = Tree[Bounds[Double], NodeBasedTreePosition]

case class BeProgram(tree: BeProgramTree) {


  def applyMapFunc[O](function: (BeBlock, BeProgramTreeContext[O]) => O): Tree[O, NodeBasedTreePosition] = {
    tree.mapWithContext((block, context) => function(block, enrichContext[O](context)))
  }

  def toPythonString: String = {

    val res = applyMapFunc[String]((block, context) => {
      block.toCode(AppLanguage.Python, enrichContext(context))
    })
    res.getData(res.rootPosition.forChild(0)).get
  }

  def appendMotionBlock(block: MotionBlock): BeProgram = ???


}


object BeProgram {

  def enrichContext[O](context: ExecutionContextInfo[NodeBasedTreePosition, BeBlock, O]): BeProgramTreeContext[O] = BeProgramTreeContext(context)

  case class BeProgramTreeContext[O](private val regularContext: ExecutionContextInfo[NodeBasedTreePosition, BeBlock, O]) extends ExecutionContextInfo[NodeBasedTreePosition, BeBlock, O] {

    override def curTraversalInformation: TraversalInformation[BeBlock, NodeBasedTreePosition] = regularContext.curTraversalInformation

    override def accessOtherResult(otherPosition: TraversalInformation[BeBlock, NodeBasedTreePosition]): O = regularContext.accessOtherResult(otherPosition)

    override def accessChildrenResults: List[O] = regularContext.accessChildrenResults

    override def accessParentResult: Option[O] = regularContext.accessParentResult

    def getChildrenWithRole(role: BeConnectionRole): List[TraversalInformation[BeBlock, NodeBasedTreePosition]] = curTraversalInformation.traversalInfoForChildren.filter(_.curValue.roleInParent == role)

    def getResultsOfRole(role: BeConnectionRole): List[O] = getChildrenWithRole(role).map(accessOtherResult)
  }


  def sampleProgram(): BeProgram = {

    val starter = BeBlockFunctionDefinition.starterBlock()
    val forward = BeBlockForward(FunctionBody)

    var tree: Tree[BeBlock, NodeBasedTreePosition] = NodeBasedTreeImpl.empty[BeBlock]()
    tree = tree.addChild(tree.rootPosition, starter)


    0.until(10).foreach(curIter => {
      tree = tree.addChild(tree.rootPosition.forChild(0), forward)
      tree = tree.addChild(tree.rootPosition.forChild(0).forChild(curIter), BeBlockValue(BeDataType.Numeric, FunctionParameter(BeDataType.Numeric), Some("This contains the value: " + curIter + "")))
    })

    val res = BeProgram(tree)
    res
  }


}
