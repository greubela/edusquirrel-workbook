package interactionPlugins.blockEnvironment.programming.blockdisplay.other

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, eventPropToProcessor, svg}
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExtensionPoint}
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, BeBlockSingleShape, BeTreeDropTarget, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockPlaceholder(extensionPoint: BeExtensionPoint, myPositionInTree: NodeBasedTreePosition) extends BeBlockSingleShape {


  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {

    val baseColorAmends = if (extensionPoint.isRequired) renderingInformation.factory.errorColorsAmend else renderingInformation.factory.defaultControlColors

    val factory = renderingInformation.renderingConfig.amendFactory

    val colorSignalAmend: Seq[Signal[L.Modifier[L.SvgElement]]] = factory.mutedColorsFunctionAmend.zip(factory.acceptingColorsAmend).zip(factory.acceptedDestinationAmends).zip(factory.errorColorsAmend).map {
      case (((muted, accepting), destination), error) => {
        val isDraggedSignal: Signal[Boolean] = renderingInformation.editorState.controllerStateVar.signal.map(_.draggingEvent.nonEmpty)
        val isAllowedSignal: Signal[Boolean] = renderingInformation.editorState.legalDropTargetsInDistanceOrder.map(_.exists(_.extensionPoint == extensionPoint))
        val isFirstSignal: Signal[Boolean] = renderingInformation.editorState.legalDropTargetsInDistanceOrder.map(_.headOption.exists(_.extensionPoint == extensionPoint))
        isDraggedSignal.combineWith(isAllowedSignal).combineWith(isFirstSignal).map { case (dragged, allowed, first) => {
          if (!dragged) muted
          else if (first) destination
          else if (allowed) accepting
          else error
        }
        }
      }
    }

    val exprRes = extensionPoint.extensionWillBeUsedAsType //
      .createShape
      .addSignalAmends(colorSignalAmend)
      .addAmends(renderingInformation.treeListener.getMouseAmendsForShape(renderingInformation.inProgram, extensionPoint))
      .addOnRendering((bounds, shape) => renderingInformation.registerDropTarget(BeTreeDropTarget(extensionPoint, myPositionInTree, bounds, shape)))
    // mouse over does not trigger while dragging???

    val cfRes = ControlFlowDirected(true, true)

    (cfRes, exprRes)
  }

}
