package todomove.webElementsOld.webElements.svg.shapes.decorations

import com.raquo.laminar.api.L
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.shapes.BeShapeDecoration

case class TriangleOverlay() extends BeShapeDecoration {

  override def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] = renderingConfig.amendFactory.activeDecorationElements

  override def getOverlayPath(rendererConfig: BeRenderingConfig, centeredAt: Point[Double]): SvgPathBuilder[Double] = {
    val sideLength = rendererConfig.controlSegmentSize * 2
    val triangleHeight = Math.sqrt(2) * rendererConfig.controlSegmentSize
    SvgPathBuilder(centeredAt)
      .moveToRel(Dimension(-sideLength / 2, -triangleHeight / 2))
      .horizontalLineWithWidth(sideLength)
      .lineToRel(Dimension(-sideLength / 2, triangleHeight))
      .lineToRel(Dimension(-sideLength / 2, -triangleHeight))
      .closePath()
  }

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = {
    val sideLength = rendererConfig.controlSegmentSize * 2
    val triangleHeight = Math.sqrt(2) * rendererConfig.controlSegmentSize
    Dimension[Double](sideLength, triangleHeight)
  }
}
