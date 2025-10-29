package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Var, svg}
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.code.usage.{BeUseValue, BeUseValueReferencing}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeProgram}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.shapes.atomic.TextShape
import interactionPlugins.blockEnvironment.programming.shapes.composite.ShapeAroundShape

case class BeBlockUseReference(
                                forValue: BeUseValueReferencing,
                                override val positionAsChild: BeChildPosition
                             ) extends BeBlockAtomar {

  def associatedExpression: BeExpression = forValue


  def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape = {
    val outerShape = BeDataType.getShape(forValue.canEvaluateTo)
    val textShape = TextShape(forValue.referencedVariable.name)
    val resShape = ShapeAroundShape(outerShape, textShape)
    resShape.addAmends(List(
      svg.fill := rendererConfig.colorPalette.grayscale(4).toWebStyleString,
    ))
  }


  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(positionAsChild = positionAsChild.copy(roleInParent = newRole))

}
