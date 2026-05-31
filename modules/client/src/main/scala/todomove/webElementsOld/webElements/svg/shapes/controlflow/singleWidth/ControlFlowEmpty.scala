package todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import todomove.webElementsOld.webElements.svg.AppSvgElement

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
