package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.expressions.BeUseValueLiteral
import contentmanagement.model.vm.types.{BeChildRole, BeDataType}
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockAtomar
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import com.raquo.laminar.api.L.svg
import contentmanagement.model.vm.expressions.defining.BeDefineVariable
import contentmanagement.model.vm.types.*
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.shapes.atomic.TextShape
import interactionPlugins.blockEnvironment.programming.shapes.composite.ShapeAroundShape
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Var, svg}
import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.expressions.*
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.*
import interactionPlugins.blockEnvironment.programming.shapes.atomic.{LiteralShape, TextShape}
import interactionPlugins.blockEnvironment.programming.shapes.composite.ShapeAroundShape
case class BeBlockUseLiteral(valueUsage: BeUseValueLiteral, roleInParent: BeChildRole) extends BeBlockAtomar{
  
  def associatedExpression: BeExpression = valueUsage

  override def render(controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape = {
    val textShape = TextShape(LanguageMap.universalMap(valueUsage.value))
    val resShape = ShapeAroundShape(LiteralShape, textShape)
    resShape.addAmends(BeShapeAmendFactory(rendererConfig).literalColorsAmend)
    
  }

  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(roleInParent = newRole)
}
