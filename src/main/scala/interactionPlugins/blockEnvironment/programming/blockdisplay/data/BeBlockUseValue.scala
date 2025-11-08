package interactionPlugins.blockEnvironment.programming.blockdisplay.data

import contentmanagement.model.language.AppLanguage.{BlockDisplay, English}
import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.usage.*
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.datatypes.LiteralShape
import contentmanagement.webElements.svg.shapes.{BeShapeAmendFactory, TextShape}
import interactionPlugins.blockEnvironment.programming.blockdisplay.*
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockUseValue(valueUsage: BeUseValue) extends BeBlock {

  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {
    val textShape = TextShape(LanguageMap.universalMap(valueUsage.getInLanguage(BlockDisplay, English)))
    val resShape = ShapeAroundShape(LiteralShape, textShape)
      .addAmends(BeShapeAmendFactory(renderingInfo.renderingConfig).literalColorsAmend)

    NestedBlockRenderer.singleExpressionLineShapeWithInfo(List(), ControlFlowEmpty(), resShape)
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
