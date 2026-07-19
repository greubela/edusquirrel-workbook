package todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import todomove.webElementsOld.webElements.svg.shapes.controlflow.*
import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapePathBased
import todomove.webElementsOld.webElements.svg.shapes.composite.*
import ControlFlowDirected.*
import ControlFlowProgramStarter.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.{ControlFlowPath, PathSegment, PathStatus}
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.compositeElements.AppDecoratedSvgElement
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, BeShapeDecoration, ShapeFactory}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.{ControlFlowConnectorBackground, ControlFlowStarterBackground}
import todomove.webElementsOld.webElements.svg.shapes.decorations.{PathSplitOverlay, TriangleOverlay}

// todo add control flow shape elements as specific elements. distinguish between unit element and control flow (control-flow = connectors, unit = ..?)
case class ControlFlowProgramStarter() extends ControlFlowShapeSingleWidth {

  override def background: BeShape.BeShapeContainerable = ControlFlowStarterBackground()

  override def minHeightInSegments: Int = 3

  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    val actualLineHeight = curLineHeight + renderingInfo.renderingConfig.controlSegmentSize
    cf
      .startNewPathAtOrigin(true, renderingInfo.renderingConfig.amendFactory.activeControlFlowAmends)
      .changeFirstOpenPath(_.changeLastPathBuilder(_
        .moveToRel(Dimension(3 * renderingInfo.renderingConfig.controlSegmentSize, actualLineHeight / 2))
        .lineToRel(Dimension(0, actualLineHeight / 2))
      ))
      .addDecoration(TriangleOverlay(), Point(centerPoint.x, centerPoint.y - renderingInfo.renderingConfig.controlSegmentSize/2))
  }

  /*
  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val seg = rendererConfig.controlSegmentSize

    val backgroundWithAmends = background.addAmends(rendererConfig.amendFactory.defaultControlColors)

    val lineStartWithAmends = ControlFlowLineStart().addAmends(rendererConfig.amendFactory.activeDecorationElements)
    val lineDownWithAmends = ControlFlowLineVertical(1.5 * seg, bounds.height - 1.5 * seg).addAmends(rendererConfig.amendFactory.activeControlFlowAmends)

    val stack = ShapeStack(List(lineDownWithAmends, lineStartWithAmends), HorizontalAlignment.Left, VerticalAlignment.Top, Map())
    val sR = stack.render(rendererConfig, bounds)

    val bR = backgroundWithAmends.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List()).addMods(List(svg.cls := "ControlFlowProgramStarter"))
  }*/
}

object ControlFlowProgramStarter {
  /*
    case class ControlFlowLineStart() extends BeShapeDecoration {
      override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
        val seg = rendererConfig.controlSegmentSize
        SvgPathBuilder(bounds.startPoint)
          .moveToRel(Dimension(3 * seg, 1.5 * seg))
          .addCenteredCircle(seg / 2)
          .closePath()
      }
    }*/
}