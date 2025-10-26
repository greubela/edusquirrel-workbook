package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Var, svg}
import contentmanagement.model.vm.expressions.defining.BeDefineVariable
import contentmanagement.model.vm.expressions.{BeExpression, BeUseValue, BeUseValueReferencing}
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeBlockContext
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.shapes.atomic.TextShape
import interactionPlugins.blockEnvironment.programming.shapes.composite.ShapeAroundShape

case class BeBlockUseReference(
                                forValue: BeUseValueReferencing,
                                roleInParent: BeChildRole
                             ) extends BeBlockAtomar {

  def associatedExpression: BeExpression = forValue

  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(roleInParent = newRole)

  override def render(controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape = {
    val outerShape = BeDataType.getShape(forValue.canEvaluateTo)
    val textShape = TextShape(forValue.referencedVariable.name)
    val resShape = ShapeAroundShape(outerShape, textShape)
    resShape.addAmends(List(
      svg.fill := rendererConfig.colorPalette.grayscale(4).toWebStyleString,
    ))
  }
  


}
