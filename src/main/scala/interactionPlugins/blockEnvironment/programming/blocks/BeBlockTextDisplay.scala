package interactionPlugins.blockEnvironment.programming.blocks

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Var, svg}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.model.vm.expressions.BeExpression
import contentmanagement.model.vm.types.BeChildRole
import contentmanagement.model.vm.types.BeChildRole.NoRole
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeBlockContext
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.shapes.atomic.TextShape

case class BeBlockTextDisplay(text: LanguageMap[HumanLanguage]) extends BeBlockAtomar {

  override def roleInParent: BeChildRole = NoRole

  override def render(controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape =
    TextShape(text).addAmends(List(
      svg.fill := rendererConfig.colorPalette.grayscale(0).toWebStyleString
    ))

  override def changeRole(newRole: BeChildRole): BeBlock = this

  val associatedExpression: BeExpression = BeExpression.NoOp
}
