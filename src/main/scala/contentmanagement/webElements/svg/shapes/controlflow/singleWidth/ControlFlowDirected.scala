package contentmanagement.webElements.svg.shapes.controlflow.singleWidth

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.shapes.BeShapeDecoration
import contentmanagement.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import contentmanagement.webElements.svg.shapes.controlflow.overlays.BeControlArrow
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowDirected.*
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class ControlFlowDirected(goesDown: Boolean, isActive: Boolean = false) extends ControlFlowShapeSingleWidth {

  override def continuesWithoutInterruption: Boolean = true

  override def minHeightInSegments: Int = 2

  private def renderWithoutArrow(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {

    val lineAmends = if (isActive) rendererConfig.amendFactory.activeControlFlowAmends else rendererConfig.amendFactory.inactiveControlFlowAmends

    val backgroundWithAmends = background.addAmends(rendererConfig.amendFactory.defaultControlColors)
    val lineDownWithAmends = ControlFlowLineVertical(0, bounds.height).addAmends(lineAmends)

    val stack = ShapeStack(List(lineDownWithAmends), HorizontalAlignment.Left, VerticalAlignment.Top, Map())
    val sR = stack.render(rendererConfig, bounds)

    val bR = backgroundWithAmends.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List()).addMods(List(svg.cls := "ControlFlowDownNoArrows"))
  }

  def renderWithSingleArrow(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val backgroundWithAmends = background.addAmends(rendererConfig.amendFactory.defaultControlColors)

    val lineAmends = if (isActive) rendererConfig.amendFactory.activeControlFlowAmends else rendererConfig.amendFactory.inactiveControlFlowAmends
    val arrowAmends = if (isActive) rendererConfig.amendFactory.activeDecorationElements else rendererConfig.amendFactory.inActiveDecorationElements
    val seg = rendererConfig.controlSegmentSize

    val arrowWithAmends = BeControlArrow(goesDown).addAmends(arrowAmends)

    val arrowHeight = seg / 5 * 9
    val distToArrow = seg / 5.0 * 1

    val arrowCenterY = seg + (bounds.height) / 2

    val arrowLeft = 3.0 * seg - seg / 5.0 * 3.0
    val arrowTop = arrowCenterY - arrowHeight / 2
    val arrowBottom = arrowCenterY + arrowHeight / 2
    val posMap = Map(arrowWithAmends -> Point(arrowLeft, arrowTop))

    val bottomLineStart = arrowTop + arrowHeight + distToArrow
    val bottomLineHeight = bounds.height - bottomLineStart

    val firstLineWithAmends = ControlFlowLineVertical(0, arrowTop - distToArrow).addAmends(lineAmends)
    val lastLineWithAmends = ControlFlowLineVertical(bottomLineStart, bottomLineHeight).addAmends(lineAmends)

    val stack = ShapeStack(List(firstLineWithAmends, arrowWithAmends, lastLineWithAmends), HorizontalAlignment.Left, VerticalAlignment.Top, posMap)

    val bR = backgroundWithAmends.render(rendererConfig, bounds)
    val sR = stack.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List()).addMods(List(svg.cls := "ControlFlowDownWithSingleArrow"))

  }


  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val seg = rendererConfig.controlSegmentSize
    if (bounds.height >= seg * 4) renderWithSingleArrow(rendererConfig, bounds)
    else renderWithoutArrow(rendererConfig, bounds)
  }
}

object ControlFlowDirected {

  case class ControlFlowLineVertical(startY: Double, height: Double) extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(3 * seg, startY))
        .lineToRel(Dimension(0, height))
    }
  }

  def ControlFlowDownActive(): ControlFlowShapeSingleWidth = ControlFlowDirected(true, true)

  def ControlFlowUpInactive(): ControlFlowShapeSingleWidth = ControlFlowDirected(false, false)

}