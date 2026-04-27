package interactionPlugins.blockEnvironment.programming.blockdisplay.data

import com.raquo.laminar.api.L
import datastructures.core.language.AppLanguage.{BlockDisplay, English}
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeContainerable
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.datatypes.LiteralShape
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeAmendFactory, ControlFlowShape, TextShape}
import datastructures.core.language.LanguageMap
import datastructures.core.vm.code.tree.BeExpressionNode
import datastructures.core.vm.code.usage.BeUseValue
import datastructures.core.vm.types.{BeDataValueLiteral, BeUseValueReference}
import interactionPlugins.blockEnvironment.programming.blockdisplay.*
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockUseValue(valueUsage: BeUseValue) extends BeBlockSingleShape {
  
  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {
    val textShape = TextShape(LanguageMap.universalMap(valueUsage.expressionIO.getInLanguage(BlockDisplay, English)))

    val (outerShape, amends): (BeShapeContainerable, Seq[L.Modifier[L.SvgElement]]) = valueUsage.value match {
      case BeDataValueLiteral(literalStr) => {
        (LiteralShape, BeShapeAmendFactory(renderingInformation.renderingConfig).literalColorsAmend)
      }
      case BeUseValueReference(reference) => {
        (reference.variableType.createContainerShape.get, BeShapeAmendFactory(renderingInformation.renderingConfig).variableColorsUsedAmend)
      }
    }
    
    val resShape = ShapeAroundShape(outerShape, textShape)
      .addAmends(amends)

    (ControlFlowEmpty(), resShape)
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
