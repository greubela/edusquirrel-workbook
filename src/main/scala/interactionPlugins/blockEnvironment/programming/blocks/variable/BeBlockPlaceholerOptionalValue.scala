package interactionPlugins.blockEnvironment.programming.blocks.variable

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, Var}
import contentmanagement.model.vm.expressions.BeExpression
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}

case class BeBlockPlaceholerOptionalValue(acceptsType: Set[BeDataType], roleInParent: BeChildRole) extends BeBlockAtomar {

  def associatedExpression: BeExpression = BeExpression.NoOp

  override def render(controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape = {
    val res = BeDataType.getShape(acceptsType)

    val factory = BeShapeAmendFactory(rendererConfig)

    val acceptColors: Signal[Boolean] = controllerStateVar.signal.map(state => state.draggingEvent.exists(draggingEvent => {
      val expressions = draggingEvent.draggedTree.getExpressions().getSubtreeInclLevel(1).values.filter(_ != BeExpression.NoOp)
      expressions.exists(_.canEvaluateTo.intersect(acceptsType).nonEmpty)
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

