package interactionPlugins.blockEnvironment.programming

import contentmanagement.datastructures.tree.*
import contentmanagement.datastructures.tree.nodeImpl.*
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.*
import contentmanagement.model.language.AppLanguage.Python
import interactionPlugins.blockEnvironment.programming.BeProgram.*
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.function.BeFunction
import interactionPlugins.blockEnvironment.programming.blocks.traits.*
import interactionPlugins.blockEnvironment.programming.blocks.variable.BeBlockUseLiteral
import interactionPlugins.blockEnvironment.programming.connection.*

type BeBlockTree = Tree[NodeBasedTreePosition, BeBlock]

type BeProgramLogicTree = Tree[NodeBasedTreePosition, BeBlockLogic]
type BeProgramDisplayTree = Tree[NodeBasedTreePosition, BeBlock]
type BeDimensionTree = Tree[NodeBasedTreePosition, Dimension[Double]]
type BePositionTree = Tree[NodeBasedTreePosition, Point[Double]]
type BeBoundsTree = Tree[NodeBasedTreePosition, Bounds[Double]]

type BeLogicContext[O] = TreeStructureAndExecutionContext[NodeBasedTreePosition, BeBlockLogic, O]
type BeBlockContext[O] = TreeStructureAndExecutionContext[NodeBasedTreePosition, BeBlock, O]

case class BeProgram(logicTree: BeProgramLogicTree) {

  def toPythonString: String = {
    val res = logicTree.mapWithContext[String](context => context.curValue.toCode(Python, context))
    res.getData(res.rootPosition.forChild(0)).get
  }

  private def structureToRoleMap(structure: TreeStructureContext[NodeBasedTreePosition, BeBlock]): Map[BeValueRole, (BeBlockValue, Int)] = {
    val childrenWithIndex = structure.childrenValues.zipWithIndex
    val valueChildrenWithIndex = childrenWithIndex.
      filter(curPair => curPair._1.isInstanceOf[BeBlockValue]).
      map(curPair => (curPair._1.asInstanceOf[BeBlockValue], curPair._2))

    val valueMap = valueChildrenWithIndex.map(curPair => curPair._1.roleInParent -> curPair).toMap
    valueMap
  }

  lazy val displayTree: BeBlockTree = {
    logicTree.map(_.asInstanceOf[BeBlock]).traverseStructureAndAddChildren(structure => {
      if (!structure.curValue.isInstanceOf[BeBlockParent]) {
        List()
      } else {
        val parent = structure.curValue.asInstanceOf[BeBlockParent]
        val valueMap = structureToRoleMap(structure)
        val insertions = parent.nodeInsertionsForDisplay(structure.childrenValues, valueMap)
 
        insertions
      }
    })
  
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
  

  trait BeTreeContext[B <: BeBlock, O] extends TreeStructureAndExecutionContext[NodeBasedTreePosition, BeBlock, O] {
    def block: B

    def getValueChildren(): List[BeBlockValue] =
      traversalInfoForChildren.filter(_.curValue.isInstanceOf[BeBlockValue]).map(_.curValue.asInstanceOf[BeBlockValue]).map(_.asInstanceOf[BeBlockValue]).toList

    def getChildrenForRole(role: BeValueRole): List[TreeStructureContext[NodeBasedTreePosition, BeBlock]] =
      traversalInfoForChildren.filter(_.curValue.isInstanceOf[BeBlockValue]).flatMap(curTravInfo => {
        val asValueBlock: BeBlockValue = curTravInfo.curValue.asInstanceOf[BeBlockValue]
        if (asValueBlock.roleInParent == role) Some(curTravInfo)
        else None
      })

    //def getResultsOfRole(role: BeValueRole): List[O] = getChildrenWithRole(role).map(accessOtherResult)

  }

  def miniProgram(): BeProgram = {

    val starter = BeFunction(LanguageMap.universalMap("start"), List(), List())

    val numParameter = FunctionParameter(0, BeDataType.Numeric)
    val forward = BeFunction(LanguageMap.universalMap("forward"), List(numParameter), List())

    val hundred = BeBlockUseLiteral(numParameter, "100", BeDataType.Numeric)


    var tree: Tree[NodeBasedTreePosition, BeBlockLogic] = NodeBasedTreeImpl.empty[BeBlockLogic]()
    tree = tree.addAsLastChild(tree.rootPosition, starter.toDefineBlockWithBody())
    tree = tree.addAsLastChild(tree.rootPosition.forChild(0), forward.toCallBlock())
    tree = tree.addAsLastChild(tree.rootPosition.forChild(0), forward.toCallBlock())
    tree = tree.addAsLastChild(tree.rootPosition.forChild(0), forward.toCallBlock())

    tree = tree.addAsLastChild(tree.rootPosition.forChild(0).forChild(1), hundred)

    BeProgram(tree)
  }


}
