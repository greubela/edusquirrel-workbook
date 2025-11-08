package contentmanagement.webElements.svg.shapes.controlflow.singleWidth

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder

case class ControlFlowEmpty() extends ControlFlowShapeSingleWidth {

  override def minHeightInSegments: Int = 1

  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    cf.changeFirstOpenPath(_.changeLastPathBuilder(_.moveToRel(Dimension(0, curLineHeight))))
  }

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {

    val backgroundWithAmends = background.addAmends(rendererConfig.amendFactory.defaultControlColors)
    backgroundWithAmends.render(rendererConfig, bounds).addMods(List(svg.cls := "ControlFlowEmpty"))

  }
}
