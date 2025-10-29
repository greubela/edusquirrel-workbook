package interactionPlugins.blockEnvironment.programming.blocks.other

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Var, svg}
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.types.BeChildRole.NoRole
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeScope}
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}
import interactionPlugins.blockEnvironment.programming.shapes.atomic.{RectangleShape, TextShape}
import interactionPlugins.blockEnvironment.programming.shapes.composite.ShapeAroundShape
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeProgram}

case class BeBlockTextDisplay(text: LanguageMap[HumanLanguage], parentPosition: NodeBasedTreePosition) extends BeBlockAtomar {

  val positionAsChild = BeChildPosition(parentPosition, BeChildRole.NoRole, BeScope.GlobalScope())
  
  def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape = {
    TextShape(text)
  }

  override def changeRole(newRole: BeChildRole): BeBlock = this

  val associatedExpression: BeExpression = BeExpression.NoOp
}
