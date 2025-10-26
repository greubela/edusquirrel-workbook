package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Var, svg}
import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.expressions.*
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.*
import interactionPlugins.blockEnvironment.programming.shapes.atomic.{LiteralShape, TextShape}
import interactionPlugins.blockEnvironment.programming.shapes.composite.ShapeAroundShape

case class BeBlockUseLiteralForVariable(
                                         valueUsage: BeUseValueLiteral,
                                         useForVariableRole: BeChildRole.ValueForVariable
                                       ) extends BeBlockAtomar {

  override val roleInParent: BeChildRole = useForVariableRole
  def associatedExpression: BeExpression = valueUsage

  override def render(controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape = {

    val textShape = TextShape(LanguageMap.universalMap(valueUsage.value)).addAmends(List(
      svg.fill := rendererConfig.colorPalette.grayscale(0).toWebStyleString,
      svg.cls := "literal-text"
    ))

    val factory = BeShapeAmendFactory(rendererConfig)
    
    val literalShape = ShapeAroundShape(LiteralShape, textShape)

    val literalAmend =
      if (useForVariableRole.associatedVariable.canAcceptValue(valueUsage)) factory.literalColorsAmend
      else factory.errorColorsAmend

    val outerShape = BeDataType.getShape(useForVariableRole.associatedVariable.canEvaluateTo.intersect(valueUsage.canEvaluateTo))
    val res = ShapeAroundShape(outerShape, literalShape.addAmends(literalAmend))

    res.addAmends(factory.lightVariableColorsAmend)    
    
  }


  override def changeRole(newRole: BeChildRole): BeBlock = BeBlockUseLiteral(valueUsage, newRole)


  /*
    protected def getDefaultColorAmends(config: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] =
        List(
        )

    override protected def getDefaultColorAmends(config: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] =
      List(
        svg.fill := RGBColor.white.toWebStyleString,
        svg.stroke := config.colorPalette.grayscale(1).toWebStyleString
      )

   */
}
