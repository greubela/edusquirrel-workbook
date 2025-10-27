package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import com.raquo.laminar.api.L.svg
import contentmanagement.model.vm.expressions.BeExpression
import contentmanagement.model.vm.expressions.defining.BeDefineVariable
import contentmanagement.model.vm.types.*
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeProgram}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.shapes.atomic.TextShape
import interactionPlugins.blockEnvironment.programming.shapes.composite.ShapeAroundShape

case class BeBlockDefineVariable(
                                  varDef: BeDefineVariable,
                                  roleInParent: BeChildRole
                                ) extends BeBlockAtomar {

  def associatedExpression: BeExpression = varDef

  def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape = {
    val outerShape = BeDataType.getShape(varDef.canEvaluateTo)
    val textShape = TextShape(varDef.name)
    val res = ShapeAroundShape(outerShape, ShapeAroundShape(outerShape, textShape))
    res.addAmends(List(
      svg.fill := rendererConfig.colorPalette.greens(4).toWebStyleString,
      svg.stroke := rendererConfig.colorPalette.greens(1).toWebStyleString
    ))
  }

  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(roleInParent = newRole)




}
