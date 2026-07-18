package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.data

import com.raquo.laminar.api.L
import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapeContainerable
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.*
import it.evadid.core.datastructures.language.AppLanguage.{BlockDisplay, English}
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlockSingleShape, RenderingInformation}
import it.evadid.vm.code.tree.BeExpressionNode
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.vm.types.{BeDataValueLiteral, BeUseValueReference}
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, BeShapeAmendFactory, ControlFlowShape, TextShape}
import todomove.webElementsOld.webElements.svg.shapes.composite.ShapeAroundShape
import todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth.ControlFlowEmpty
import todomove.webElementsOld.webElements.svg.shapes.datatypes.LiteralShape

case class BeBlockUseValue(valueUsage: BeUseValue) extends BeBlockSingleShape {
  
  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {
    val textShape = TextShape(LanguageMap.universalMap(valueUsage.expressionIO.toStringInLanguage(BlockDisplay, English)))

    val (outerShape, amends): (BeShapeContainerable, Seq[L.Modifier[L.SvgElement]]) = valueUsage.value match {
      case BeDataValueLiteral(literalStr) => {
        (LiteralShape, BeShapeAmendFactory(renderingInformation.renderingConfig).literalColorsAmend)
      }
      case BeUseValueReference(reference) => {
        (BeDataTypeShapeAdapter.containerShapeFor(reference.variableType).get, BeShapeAmendFactory(renderingInformation.renderingConfig).variableColorsUsedAmend)
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
