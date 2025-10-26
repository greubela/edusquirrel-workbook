package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.expressions.{BeExpression, BeUseValue}
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}

case class BeBlockPlaceholderMissingValue(variable: BeUseValue, roleInParent: BeChildRole) extends BeBlockAtomar {

  def associatedExpression: BeExpression = variable

  override def render(controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape = {
    val res = BeDataType.getShape(variable.canEvaluateTo)

    res.addAmends(BeShapeAmendFactory(rendererConfig).errorColorsAmend)
  }

  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(roleInParent = newRole)


}
