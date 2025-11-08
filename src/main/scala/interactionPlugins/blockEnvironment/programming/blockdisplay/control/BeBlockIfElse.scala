package interactionPlugins.blockEnvironment.programming.blockdisplay.control

import contentmanagement.model.vm.code.controlStructures.BeIfElse
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.BeChildRole.{BodySequence, ConditionInControlStructure}
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.{ControlFlowCross, IfElseSplit, IfElseUnion}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowDirected
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer.{ControlFlowDecreaseLine, ControlFlowIncreaseLine, ControlFlowReplaceLine}
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockIfElse(expr: BeIfElse) extends BeBlock {

  
  override def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    var res = NestedBlockRenderer.empty()

    val condExpr = renderedChildren.find(_._1.childPosition.roleInParent == ConditionInControlStructure)
    val thenExpr = renderedChildren.find(_._1.childPosition.roleInParent == BodySequence(0))
    val elseExpr = renderedChildren.find(_._1.childPosition.roleInParent == BodySequence(1))

    assert(condExpr.nonEmpty && thenExpr.nonEmpty && elseExpr.nonEmpty, "if/else MUST have cond/then/else, but: " + condExpr.nonEmpty + "/" + thenExpr.nonEmpty + "/" + elseExpr.nonEmpty )

    val splitLine = ControlFlowIncreaseLine(IfElseSplit(), Some(condExpr.get._3.expressionShapeWithoutIntendation), ControlFlowDirected(true, false))
    val crossLine = ControlFlowReplaceLine(ControlFlowCross(), None, ControlFlowDirected(true, false))
    val unionLine = ControlFlowDecreaseLine(IfElseUnion(), None)

    res = res.withAppendedLine(splitLine)
    for(curLine <- thenExpr.get._3.allLines){
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
