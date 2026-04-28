package contentmanagement.webElements.svg.shapes.composite

import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.compositeElements.AppGroupSvgElement
import contentmanagement.webElements.svg.shapes.BeShape
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeComposite
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}

import scala.collection.mutable

case class ManualPositionElement(resultShape: BeShape, relativeOffset: Point[Double], shapeDimension: Dimension[Double])

abstract class BoxManualPositioning() extends BeShapeComposite {

  private val cache: mutable.HashMap[BeRenderingConfig, List[ManualPositionElement]] = mutable.HashMap()

  def calcOffsetsAndDimensions(config: BeRenderingConfig): List[ManualPositionElement]

  def getOffsetAndDimension(config: BeRenderingConfig): List[ManualPositionElement] = {
    if (cache.contains(config)) {
      cache(config)
    } else {
      val result = calcOffsetsAndDimensions(config)
      cache.put(config, result)
      result
    }
  }

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val renderedChildren = getOffsetAndDimension(rendererConfig).map(element => {
      val curShapeBounds = bounds.startPoint.moveWithDimension(element.relativeOffset.asDimension).withDimension(element.shapeDimension)
      element.resultShape.render(rendererConfig, curShapeBounds)
    })
    AppGroupSvgElement(renderedChildren)
  }

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = {
    val bounds = getOffsetAndDimension(rendererConfig).map(element => element.relativeOffset.withDimension(element.shapeDimension))
    Dimension(bounds.map(_.endX).maxOption.getOrElse(0.0), bounds.map(_.endY).maxOption.getOrElse(0.0))
  }


}

