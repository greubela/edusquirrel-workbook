package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory, TextShape}
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import com.raquo.laminar.api.L.svg
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.types.*
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.shapes.composite.ShapeAroundShape
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Var, svg}
import contentmanagement.model.language.AppLanguage.{BlockDisplay, English}
import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.*
import interactionPlugins.blockEnvironment.programming.shapes.datatypes.LiteralShape
import interactionPlugins.blockEnvironment.programming.shapes.composite.ShapeAroundShape
import contentmanagement.model.vm.code.usage.*

case class BeBlockUseValue(valueUsage: BeUseValue) extends BeBlock {
  
  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {
    val textShape = TextShape(LanguageMap.universalMap(valueUsage.getInLanguage(BlockDisplay, English)))
    val resShape = ShapeAroundShape(LiteralShape, textShape)
      .addAmends(BeShapeAmendFactory(renderingInfo.renderingConfig).literalColorsAmend)
    
    NestedBlockRenderer.fromShape(resShape)
  }

  
  /*
  literal for variable: 
    def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape = {

    val textShape = TextShape(LanguageMap.universalMap(valueUsage.value)).addAmends(List(
      svg.fill := rendererConfig.colorPalette.grayscale(0).toWebStyleString,
      svg.cls := "literal-text"
    ))

    val factory = BeShapeAmendFactory(rendererConfig)

    val literalShape = ShapeAroundShape(LiteralShape, textShape)

    val literalAmend = if (forVariable.canAcceptValue(valueUsage)) factory.literalColorsAmend
    else factory.errorColorsAmend

    val outerShape = BeDataType.getShape(forVariable.canEvaluateTo.intersect(valueUsage.canEvaluateTo))
    val res = ShapeAroundShape(outerShape, literalShape.addAmends(literalAmend))

    res.addAmends(factory.lightVariableColorsAmend)

  }
   */
}
