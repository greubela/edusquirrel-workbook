package interactionPlugins.blockEnvironment.programming.blockdisplay.control

import datastructures.core.vm.types.BeChildRole.{BodySequence, ConditionInControlStructure}
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.*
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowDownUp
import contentmanagement.webElements.svg.shapes.special.nested.NestedControlStructureShape
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowAndExpressionShape, ControlFlowShape}
import datastructures.core.tree.TreeStructureContext
import datastructures.core.tree.nodeImpl.NodeBasedTreePosition
import datastructures.core.vm.code.controlStructures.BeWhile
import datastructures.core.vm.code.tree.BeExpressionNode
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer.{ControlFlowDecreaseLine, ControlFlowIncreaseLine}

case class BeBlockWhile(whileExpr: BeWhile) extends BeBlock {


  override def renderNested(
                             structure: TreeStructureContext[NodeBasedTreePosition, (BeExpressionNode, BeBlock)],
                             childResults: Map[(BeExpressionNode, BeBlock), ControlFlowAndExpressionShape],
                             renderingInfo: RenderingInformation
                           ): ControlFlowAndExpressionShape = {

    val resultList: List[((BeExpressionNode, BeBlock), ControlFlowAndExpressionShape)] = childResults.toList

    val conditionChildOp = resultList.find(_._1._1.childPosition.roleInParent == ConditionInControlStructure)
    val condition: BeShape = conditionChildOp.get._2.expressionShapeOrEverything
    val bodyShapes: List[BeShape] = resultList.filter(_._1._1.childPosition.roleInParent == BodySequence(0)).map(_._2)

    new NestedControlStructureShape {

      override def leftColumnWidth(config: BeRenderingConfig): Double = config.controlSegmentSize * 6

      override def totalControlFlowWidth(config: BeRenderingConfig): Double = config.controlSegmentSize * 12

      override def getControlFlowChangeElements: List[ControlFlowShape] = List(RepetitionSplit(), RepetitionUnion())

      override def getControlFlowExpressionElements: List[Option[BeShape]] = List(Some(condition), None)

      override def getBodiesBetweenControlFlowChanges: List[List[BeShape]] = List(bodyShapes)
    }

  }

  override def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {


    val condExpr = renderedChildren.find(_._1.childPosition.roleInParent == ConditionInControlStructure)
    val bodyExpr = renderedChildren.find(_._1.childPosition.roleInParent == BodySequence(0))

    assert(bodyExpr.nonEmpty && bodyExpr.nonEmpty, "while MUST have cond/body, but: " + bodyExpr.nonEmpty + "/" + bodyExpr.nonEmpty)

    val splitLine = ControlFlowIncreaseLine(RepetitionSplit(), Some(condExpr.get._3.expressionShapeWithoutIntendation), ControlFlowDownUp(false))
    val unionLine = ControlFlowDecreaseLine(RepetitionUnion(), None)

    var res = NestedBlockRenderer.empty()
    res = res.withAppendedLine(splitLine)
    for (curLine <- bodyExpr.get._3.allLines) {
      res = res.withAppendedLine(curLine)
    }
    res = res.withAppendedLine(unionLine)


    res

  }
}
