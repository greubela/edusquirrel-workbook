package contentmanagement.webElements.shapes.meta

import contentmanagement.webElements.svg.AppSvgElement
import datastructures.core.geometry.Dimension
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

trait ShapeLogic[T : Fractional] {

  def renderToMinSize(myInfo: ShapeInfo[T], renderingInfo: BeRenderingConfig): AppSvgElement

  def renderWithMaxSize(myInfo: ShapeInfo[T], renderingInfo: BeRenderingConfig, maxSize: Dimension[T]): AppSvgElement
  
}
