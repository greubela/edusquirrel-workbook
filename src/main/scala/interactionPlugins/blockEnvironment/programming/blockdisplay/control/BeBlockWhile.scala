package interactionPlugins.blockEnvironment.programming.blockdisplay.control

import contentmanagement.model.vm.code.controlStructures.BeWhile
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.BeChildRole.{BodySequence, ConditionInControlStructure}
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.*
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.{ControlFlowDirected, ControlFlowDownUp}
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer.{ControlFlowDecreaseLine, ControlFlowIncreaseLine, ControlFlowReplaceLine}

case class BeBlockWhile(whileExpr: BeWhile) extends BeBlock {

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
