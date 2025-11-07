package contentmanagement.webElements.svg.shapes.controlflow.singleWidth

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.webElements.svg.shapes.controlflow.*
import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import contentmanagement.webElements.svg.shapes.BeShape.BeShapePathBased
import contentmanagement.webElements.svg.shapes.composite.*
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeDecoration, ShapeFactory}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowStarterBackground
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowDirected.ControlFlowLineVertical
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowProgramStarter.ControlFlowLineStart

// todo add control flow shape elements as specific elements. distinguish between unit element and control flow (control-flow = connectors, unit = ..?)
case class ControlFlowProgramStarter() extends ControlFlowShapeSingleWidth {

  override def background: BeShape.BeShapeContainerable = ControlFlowStarterBackground()

  override def continuesWithoutInterruption: Boolean = false

  override def minHeightInSegments: Int = 3

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val seg = rendererConfig.controlSegmentSize

    val backgroundWithAmends = background.addAmends(rendererConfig.amendFactory.defaultControlColors)

    val lineStartWithAmends = ControlFlowLineStart().addAmends(rendererConfig.amendFactory.activeDecorationElements)
    val lineDownWithAmends = ControlFlowLineVertical(1.5*seg, bounds.height-1.5*seg).addAmends(rendererConfig.amendFactory.activeControlFlowAmends)

    val stack = ShapeStack(List(lineDownWithAmends, lineStartWithAmends), HorizontalAlignment.Left, VerticalAlignment.Top, Map())
    val sR = stack.render(rendererConfig, bounds)

    val bR = backgroundWithAmends.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List()).addMods(List(svg.cls := "ControlFlowProgramStarter"))
  }
}
object ControlFlowProgramStarter {

  case class ControlFlowLineStart() extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(3 * seg, 1.5*seg))
        .addCenteredCircle(seg/2)
        .closePath()
    }
  }
}