package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.usage.BeUseValue
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}

case class BeBlockPlaceholderMissingValue(variable: BeUseValue, override val positionAsChild: BeChildPosition) extends BeBlockAtomar {

  def associatedExpression: BeExpression = variable

  def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape = {
    val res = BeDataType.getShape(variable.canEvaluateTo)

    res.addAmends(BeShapeAmendFactory(rendererConfig).errorColorsAmend)
  }


  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(positionAsChild = positionAsChild.copy(roleInParent = newRole))

}
