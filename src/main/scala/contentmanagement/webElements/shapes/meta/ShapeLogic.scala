package contentmanagement.webElements.shapes.meta

import contentmanagement.model.geometry.Dimension
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

trait ShapeLogic[T : Fractional] {

  def renderToMinSize(myInfo: ShapeInfo[T], renderingInfo: BeRenderingConfig): AppSvgElement

  def renderWithMaxSize(myInfo: ShapeInfo[T], renderingInfo: BeRenderingConfig, maxSize: Dimension[T]): AppSvgElement
  
}
