package interactionPlugins.blockEnvironment.programming.blocks.data

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Var, svg}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.*
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.shapes.{BeShape, TextShape}
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeRenderingConfig, BeTreeDisplayConfig}
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeProgram}

case class BeBlockDefineVariable(
                                  varDef: BeDefineVariable,
                                ) extends BeBlock {


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {
    val outerShape = varDef.canEvaluateTo.createContainerShape.get
    val textShape = TextShape(varDef.name)
    val res = ShapeAroundShape(outerShape, ShapeAroundShape(outerShape, textShape))
      .addAmends(List(
        svg.fill := renderingInfo.renderingConfig.colorPalette.greens(4).toWebStyleString,
        svg.stroke := renderingInfo.renderingConfig.colorPalette.greens(1).toWebStyleString
      ))
    NestedBlockRenderer.singleExpressionLineShapeWithInfo(List(), ControlFlowEmpty(), res)
  }
  


}
