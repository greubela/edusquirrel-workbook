package todomove.webElementsOld.webElements.svg.shapes.controlflow

import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapePathBased
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilder
import todomove.webElementsOld.webElements.svg.shapes.DecorationFactory

case class ControlFlowStarterBackground() extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =
    DecorationFactory[Double](config).buildControlFlowStart(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0, 0)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0, 0)

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    Dimension[Double](config.controlSegmentSize * 6, config.controlSegmentSize * 3)
  }

}