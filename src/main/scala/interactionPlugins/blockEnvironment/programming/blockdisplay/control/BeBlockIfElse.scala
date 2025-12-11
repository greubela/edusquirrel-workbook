package interactionPlugins.blockEnvironment.programming.blockdisplay.control

import contentmanagement.datastructures.tree.*
import contentmanagement.datastructures.tree.nodeImpl.*
import contentmanagement.model.vm.code.controlStructures.BeIfElse
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.BeChildRole.{BodySequence, ConditionInControlStructure}
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.{ControlFlowCross, ControlFlowShapeDoubleWidth, IfElseSplit, IfElseUnion}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowDirected
import contentmanagement.webElements.svg.shapes.nested.NestedControlStructureShape
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowAndExpressionShape, ControlFlowShape}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer.{ControlFlowDecreaseLine, ControlFlowIncreaseLine, ControlFlowReplaceLine}

case class BeBlockIfElse(expr: BeIfElse) extends BeBlock {


  override def renderNested(
                             structure: TreeStructureContext[NodeBasedTreePosition, (BeExpressionNode, BeBlock)],
                             childResults: Map[(BeExpressionNode, BeBlock), ControlFlowAndExpressionShape],
                             renderingInfo: RenderingInformation
                           ): ControlFlowAndExpressionShape = {

    val resultList: List[((BeExpressionNode, BeBlock), ControlFlowAndExpressionShape)] = childResults.toList

    val conditionChildOp = resultList.find(_._1._1.childPosition.roleInParent == ConditionInControlStructure)
    val condition: BeShape = conditionChildOp.get._2.expressionShapeOrEverything
    val thenShapes: List[BeShape] = resultList.filter(_._1._1.childPosition.roleInParent == BodySequence(0)).map(_._2)
    val elseShapes: List[BeShape] = resultList.filter(_._1._1.childPosition.roleInParent == BodySequence(1)).map(_._2)

    new NestedControlStructureShape {

      override def leftColumnWidth(config: BeRenderingConfig): Double = config.controlSegmentSize * 6

      override def totalControlFlowWidth(config: BeRenderingConfig): Double = config.controlSegmentSize * 12

      override def getControlFlowChangeElements: List[ControlFlowShape] = List(IfElseSplit(), ControlFlowCross(), IfElseUnion())

      override def getControlFlowExpressionElements: List[Option[BeShape]] = List(Some(condition), None, None)

      override def getBodiesBetweenControlFlowChanges: List[List[BeShape]] = List(thenShapes, elseShapes)
    }

  }

  override def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    var res = NestedBlockRenderer.empty()

    val condExpr = renderedChildren.find(_._1.childPosition.roleInParent == ConditionInControlStructure)
    val thenExpr = renderedChildren.find(_._1.childPosition.roleInParent == BodySequence(0))
    val elseExpr = renderedChildren.find(_._1.childPosition.roleInParent == BodySequence(1))

    assert(condExpr.nonEmpty && thenExpr.nonEmpty && elseExpr.nonEmpty, "if/else MUST have cond/then/else, but: " + condExpr.nonEmpty + "/" + thenExpr.nonEmpty + "/" + elseExpr.nonEmpty)

    val splitLine = ControlFlowIncreaseLine(IfElseSplit(), Some(condExpr.get._3.expressionShapeWithoutIntendation), ControlFlowDirected(true, false))
    val crossLine = ControlFlowReplaceLine(ControlFlowCross(), None, ControlFlowDirected(true, false))
    val unionLine = ControlFlowDecreaseLine(IfElseUnion(), None)

    res = res.withAppendedLine(splitLine)
    for (curLine <- thenExpr.get._3.allLines) {
      res = res.withAppendedLine(curLine)
    }
    res = res.withAppendedLine(crossLine)
    for (curLine <- elseExpr.get._3.allLines) {
      res = res.withAppendedLine(curLine)
    }
    res = res.withAppendedLine(unionLine)

    res
  }


}
