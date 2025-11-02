package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Var, svg}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.*
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.shapes.composite.ShapeAroundShape
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, TextShape}
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
    NestedBlockRenderer.fromShape(res)
  }
  


}
