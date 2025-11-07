package contentmanagement.webElements.svg.shapes.controlflow.singleWidth

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg

import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class ControlFlowEmpty() extends ControlFlowShapeSingleWidth {

  override def continuesWithoutInterruption: Boolean = false

  override def minHeightInSegments: Int = 1

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {

    val backgroundWithAmends = background.addAmends(rendererConfig.amendFactory.defaultControlColors)
    backgroundWithAmends.render(rendererConfig, bounds).addMods(List(svg.cls := "ControlFlowEmpty"))

  }
}
