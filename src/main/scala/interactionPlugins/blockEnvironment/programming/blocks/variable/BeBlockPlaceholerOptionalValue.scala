package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, Var}
import contentmanagement.model.vm.expressions.BeExpression
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}

case class BeBlockPlaceholerOptionalValue(acceptsType: Set[BeDataType], roleInParent: BeChildRole) extends BeBlockAtomar {

  def associatedExpression: BeExpression = BeExpression.NoOp

  def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape = {
    val res = BeDataType.getShape(acceptsType)

    val factory = BeShapeAmendFactory(rendererConfig)

    val acceptColors: Signal[Boolean] = controllerStateVar.signal.map(state => state.draggingEvent.exists(draggingEvent => {
      val draggedTreeEvalautesTo = draggingEvent.draggedProgram.asExpression.canEvaluateTo
      draggedTreeEvalautesTo.intersect(acceptsType).nonEmpty
    }))
    
    val signalAmends =
      factory.signalBasedAmendChooser(
        acceptColors,
        factory.acceptingColorsAmend,
        factory.mutedColorsAmend
      )

    res.addSignalAmends(signalAmends)
  }


  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(roleInParent = newRole)


}

