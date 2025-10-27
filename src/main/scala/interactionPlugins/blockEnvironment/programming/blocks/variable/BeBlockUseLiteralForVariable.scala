package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Var, svg}
import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.expressions.*
import contentmanagement.model.vm.expressions.defining.BeDefineVariable
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.*
import interactionPlugins.blockEnvironment.programming.shapes.atomic.{LiteralShape, TextShape}
import interactionPlugins.blockEnvironment.programming.shapes.composite.ShapeAroundShape
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}

case class BeBlockUseLiteralForVariable(
                                         valueUsage: BeUseValueLiteral,
                                         forVariable: BeDefineVariable,
                                         override val positionAsChild: BeChildPosition
                                       ) extends BeBlockAtomar {

  def associatedExpression: BeExpression = valueUsage


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

  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(positionAsChild = BeChildPosition(positionAsChild.parentPosition, newRole))

}
