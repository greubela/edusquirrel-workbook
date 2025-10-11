package interactionPlugins.blockEnvironment.programming.rendering

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.*

trait BeBlockDisplayManager() {

  def stroke(config: BeRendererConfig): String

  def fill(config: BeRendererConfig): String

  def shapeFactory: Bounds[Double] => AppSvgElement

  def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double]

  def calcDisplaySize(config: BeRendererConfig, minSizeTree: BeDimensionTree, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = minSizeTree.getData(context.curPosition).get
}


object BeBlockDisplayManager {
  def calcMinSizeStringBased(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]], string: String): Dimension[Double] = {
    config.appFont.measureText(string).increaseSize(config.paddingSmall)
  }
}








