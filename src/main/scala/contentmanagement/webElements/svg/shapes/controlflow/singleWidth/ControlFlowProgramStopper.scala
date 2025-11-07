package contentmanagement.webElements.svg.shapes.controlflow.singleWidth

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeDecoration}
import contentmanagement.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowDirected.ControlFlowLineVertical
import contentmanagement.webElements.svg.shapes.controlflow.{ControlFlowStarterBackground, ControlFlowStopperBackground}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowProgramStopper.ControlFlowLineEnd
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class ControlFlowProgramStopper() extends ControlFlowShapeSingleWidth {

  override def background: BeShape.BeShapeContainerable = ControlFlowStopperBackground()

  override def continuesWithoutInterruption: Boolean = false

  override def minHeightInSegments: Int = 3

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val backgroundWithAmends = background.addAmends(rendererConfig.amendFactory.defaultControlColors)

    val lineDownWithAmends = ControlFlowLineVertical(0, bounds.height - rendererConfig.controlSegmentSize).addAmends(rendererConfig.amendFactory.activeControlFlowAmends)
    val lineEndWithAmends = ControlFlowLineEnd(bounds.height - rendererConfig.controlSegmentSize).addAmends(rendererConfig.amendFactory.activeControlFlowAmends)

    val stack = ShapeStack(List(lineDownWithAmends, lineEndWithAmends), HorizontalAlignment.Left, VerticalAlignment.Top, Map())
    val sR = stack.render(rendererConfig, bounds)

    val bR = backgroundWithAmends.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List()).addMods(List(svg.cls := "ControlFlowProgramStarter"))
  }

}

object ControlFlowProgramStopper {

  case class ControlFlowLineEnd(y: Double) extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(2 * seg, y))
        .horizontalLineWithWidth(2 * seg)
    }
  }

}
