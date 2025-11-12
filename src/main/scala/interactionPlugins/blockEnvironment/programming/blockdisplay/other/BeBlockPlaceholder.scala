package interactionPlugins.blockEnvironment.programming.blockdisplay.other

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, eventPropToProcessor, svg}
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExtensionPoint}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, BeTreeDropTarget, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockPlaceholder(extensionPoint: BeExtensionPoint, myPositionInTree: NodeBasedTreePosition) extends BeBlock {


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val baseColorAmends = if (extensionPoint.isRequired) renderingInfo.factory.errorColorsAmend else renderingInfo.factory.defaultControlColors

    val factory = renderingInfo.renderingConfig.amendFactory

    val colorSignalAmend: Seq[Signal[L.Modifier[L.SvgElement]]] = factory.mutedColorsFunctionAmend.zip(factory.acceptingColorsAmend).zip(factory.acceptedDestinationAmends).zip(factory.errorColorsAmend).map {
      case (((muted, accepting), destination), error) => {
        val isDraggedSignal: Signal[Boolean] = renderingInfo.editorState.controllerStateVar.signal.map(_.draggingEvent.nonEmpty)
        val isAllowedSignal: Signal[Boolean] = renderingInfo.editorState.legalDropTargetsInDistanceOrder.map(_.exists(_.extensionPoint == extensionPoint))
        val isFirstSignal: Signal[Boolean] = renderingInfo.editorState.legalDropTargetsInDistanceOrder.map(_.headOption.exists(_.extensionPoint == extensionPoint))
        isDraggedSignal.combineWith(isAllowedSignal).combineWith(isFirstSignal).map { case (dragged, allowed, first) => {
          if (!dragged) muted
          else if (first) destination
          else if(allowed) accepting
          else error
        }
        }
      }
    }

    val res = extensionPoint.extensionWillBeUsedAsType //
      .createShape
      .addSignalAmends(colorSignalAmend)
      .addAmends(renderingInfo.treeListener.getMouseAmendsForShape(renderingInfo.inProgram, extensionPoint))
      .addOnRendering((bounds, shape) => renderingInfo.registerDropTarget(BeTreeDropTarget(extensionPoint, myPositionInTree, bounds, shape)))
    // mouse over does not trigger while dragging???


    NestedBlockRenderer.singleExpressionLineShapeWithInfo(List(), ControlFlowDirected(true, true), res)
  }

}
