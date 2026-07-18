package todomove.webElementsOld.webElements.svg.shapes.decorations

import com.raquo.laminar.api.L
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilder
import todomove.webElementsOld.webElements.svg.shapes.{BeShapeDecoration, DecorationFactory}

case class ControlFlowUnion(pointsDown: Boolean = true, isActive: Boolean = true, cleanOrigin: Boolean = false) extends BeShapeDecoration {

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension(rendererConfig.controlSegmentSize , rendererConfig.controlSegmentSize )

  def getOverlayPath(rendererConfig: BeRenderingConfig, center: Point[Double]): SvgPathBuilder[Double] = {
    if (pointsDown) DecorationFactory[Double](rendererConfig).controlFlowUnionDown(center, cleanOrigin)
    else DecorationFactory[Double](rendererConfig).controlFlowUnionUp(center, cleanOrigin)
  }

  def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]] =
    if (isActive) renderingConfig.amendFactory.activeDecorationElements
    else renderingConfig.amendFactory.inActiveDecorationElements
}
