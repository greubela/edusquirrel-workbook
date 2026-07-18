package todomove.webElementsOld.webElements.shapes.meta

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.AppSvgElement

trait ShapeLogic[T : Fractional] {

  def renderToMinSize(myInfo: ShapeInfo[T], renderingInfo: BeRenderingConfig): AppSvgElement

  def renderWithMaxSize(myInfo: ShapeInfo[T], renderingInfo: BeRenderingConfig, maxSize: Dimension[T]): AppSvgElement
  
}
