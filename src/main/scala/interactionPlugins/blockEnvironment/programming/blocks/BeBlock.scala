package interactionPlugins.blockEnvironment.programming.blocks

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.BeDataType
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.editor.elements.BeTreeControllerConfig
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}

case class RenderingInformation(inProgram: BeProgram, displayConfig: BeDisplayConfig, renderingConfig: BeRenderingConfig, treeListener: BeTreeControllerConfig, controllerStateVar: Var[BeControllerState]) {
  lazy val factory = BeShapeAmendFactory(renderingConfig)
}


trait BeBlock {

  def render(structure: BeBlockRenderingContext, renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)] = {
      structure
        .traversalInfoForChildren
        .zip(structure.accessChildrenResults)
        .map((curStructure, childRes) => (
          curStructure.curValue._1,
          curStructure.curValue._2,
          childRes
        )
        )
    }

    render(renderedChildren, renderingInfo)
  }

  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer

}




/*sealed case class BeBlockTextDisplay(text: LanguageMap[HumanLanguage]) extends BeBlock {

}*/

/*
override def render(structure: BeBlockRenderingContext, renderingInfo: RenderingInformation): BlockRenderingBuilder = {

  val childrenExprNode: List[BeExpressionNode] = structure.childrenValues.map(_._1)
  val childrenBlocks: List[BeBlock] = structure.childrenValues.map(_._2)

  val childrenMain: List[(BeExpressionNode, Option[BeShape])] = {
    childrenExprNode.zip(childrenBlocks.map(_.renderMain(structure, renderingInfo)))
  }

  val childrenControlFlows = structure.childrenValues.map(_._2).map(_.renderControlFlowShape())

  ???
}

def combineAll(renderingInfo: RenderingInformation,
               childrenMain: List[(BeExpressionNode, BeShape)]

childrenControlFlows: List[(BeExpressionNode, BeShape)]
,
childrenControlFlows: List[BeShape]
):
BeShape = {}

def renderMain(structure: BeBlockRenderingContext, renderingInfo: RenderingInformation): Option[BeShape]


def render(structure: BeBlockRenderingContext, renderingInfo: RenderingInformation): BeShape


def renderControlFlowShape(renderingInfo: RenderingInformation): BeShape

def renderNavigations(structure: BeBlockRenderingContext, renderingInfo: RenderingInformation): List[BeShape] = List()

def renderWarnings(structure: BeBlockRenderingContext, renderingInfo: RenderingInformation): List[BeShape] = List()
*/


/*sealed trait BeBlockForExpression extends BeBlock {

  def expr: BeExpression


  def renderWithControlFlow(children: List[(BeExpressionNode, BeShape)], renderingInfo: RenderingInformation): BeShape = {}

  def renderMain(childrenMainShapes: List[(BeExpressionNode, BeShape)], renderingInfo: RenderingInformation): BeShape


  def renderNavigations(children: List[(BeExpressionNode, BeShape)], renderingInfo: RenderingInformation): List[BeShape] = List()

}*/


/*
abstract class BeBlockParent extends BeBlock {

  /*
  override def render(inProgram: BeProgram, treeControllerConfig: BeTreeControllerConfig, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig, structure: BeBlockContext): BeShape = {
    val childTravInfo = structure.traversalInfoForChildren

    val childrenRefs: List[ReferenceExistingBlock] = structure.traversalInfoForChildren.zipWithIndex.map((curChildInfo, curChildIndex) => {
      ReferenceExistingBlock(curChildInfo, curChildIndex, curChildInfo.curValue)
    })

    val displayChildren: List[BeBlockReference] = getDisplayChildren(structure.curPosition, treeControllerConfig, displayConfig, childrenRefs)

    val renderedDisplayChildren: List[(BeBlockReference, BeShape)] = displayChildren.map(curChild => {
      val svgElement = curChild match {
        case ReferenceExistingBlock(childStructure, nrInChildList, block) => block.render(inProgram, treeControllerConfig, controllerStateVar, displayConfig, rendererConfig, childStructure)
        case NewBlock(valueChild) => valueChild.render(inProgram, treeControllerConfig, controllerStateVar, displayConfig, rendererConfig)
        //        protected def render(controllerState: BeControllerState, displayConfig: BeDisplayConfig, config: BeRenderingConfig): AppSvgElement

      }
      (curChild, svgElement)
    })
    render(inProgram, controllerStateVar, rendererConfig, renderedDisplayChildren)
      .addAmends(treeControllerConfig.getMouseAmendsForPosition(inProgram, positionAsChild))
    //  .addAmends(treeControllerConfig.getDragDropAmends(inProgram))

  }

  def getDisplayChildren(myPosition: NodeBasedTreePosition, treeControllerConfig: BeTreeControllerConfig, displayConfig: BeDisplayConfig, existingChildren: List[ReferenceExistingBlock]): List[BeBlockReference]

  protected def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape
*/
}

abstract class BeBlockAtomar extends BeBlock {

  /*
  override def render(inProgram: BeProgram, listener: BeTreeControllerConfig, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig, structure: BeBlockContext): BeShape = {
    render(inProgram, controllerStateVar, displayConfig, rendererConfig).addAmends(listener.getMouseAmendsForPosition(inProgram, positionAsChild))
  }

  def render(inProgram: BeProgram, listener: BeTreeControllerConfig, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape
  = render(inProgram, controllerStateVar, displayConfig, rendererConfig).addAmends(listener.getMouseAmendsForPosition(inProgram, positionAsChild))

  def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape

  def associatedExpression: BeExpression

  override def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression = associatedExpression

   */

}

*/


/*
  var finishedLines:

  var finishedLines: mutable.ListBuffer[(List[BeShape], Option[BlockLineBuilder])] = mutable.ListBuffer()
  var unfinishedLine = BlockLineBuilder()

  val curControlShapeStack: mutable.ListBuffer[BeShape] = mutable.ListBuffer[BeShape]()

  def pushLine(withControlShape: BeShape): BlockRenderingBuilder = {
    finishedLines.append((curControlShapeStack.toList ++ List(withControlShape), Some(unfinishedLine)))
    unfinishedLine = BlockLineBuilder()
    this
  }

  def pushDoubleControlFlowLine(controlFlowLeft: BeShape, controlFlowRight: BeShape, line: Option[BlockLineBuilder]): BlockRenderingBuilder = {
    finishedLines += (curControlShapeStack.toList ++ List(controlFlowLeft, controlFlowRight), line)
  }

  def increaseLevel(controlFlowLeft: BeShape): BlockRenderingBuilder = {
    curControlShapeStack += controlFlowLeft
    this
  }

  def decreaseLevel(): BlockRenderingBuilder = {
    curControlShapeStack.remove(curControlShapeStack.length - 1)
    this
  }

  def buildShape(): BeShape = {
    val controlShapeLineWidth: List[Double] = List()

    ???
  }
*/
/*
case class BlockLineShape() {

  val infos: mutable.ListBuffer[(BeExpression, BeShape, BeInfo)] = mutable.ListBuffer()
  val navs: mutable.ListBuffer[(BeExpression, BeExpression)] = mutable.ListBuffer()
  val curShape: mutable.ListBuffer[(BeExpression, BeShape)] = mutable.ListBuffer()
  val controlFlowRight: mutable.ListBuffer[BeShape] = mutable.ListBuffer()


  def addNavRef(refSource: BeExpression, refTarget: BeExpression): BlockLineBuilder = ???

  def changeMain(transform: BeShape => BeShape): BlockLineBuilder = ???

}*/