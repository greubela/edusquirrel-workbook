package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.other

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.data.BeDataTypeShapeAdapter

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, eventPropToProcessor, svg}
import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, BeBlockSingleShape, BeTreeDropTarget, RenderingInformation}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import it.evadid.workbook.vm.code.tree.{BeExpressionNode, BeExtensionPoint}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth.ControlFlowDirected
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, ControlFlowShape}

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

    val exprRes = BeDataTypeShapeAdapter.shapeFor(extensionPoint.extensionWillBeUsedAsType)
      .addSignalAmends(colorSignalAmend)
      .addAmends(renderingInformation.treeListener.getMouseAmendsForShape(renderingInformation.inProgram, extensionPoint))
      .addOnRendering((bounds, shape) => renderingInformation.registerDropTarget(BeTreeDropTarget(extensionPoint, myPositionInTree, bounds, shape)))
    // mouse over does not trigger while dragging???

    val cfRes = ControlFlowDirected(true, true)

    (cfRes, exprRes)
  }

}
