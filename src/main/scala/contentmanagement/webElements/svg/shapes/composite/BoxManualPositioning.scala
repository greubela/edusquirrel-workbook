package contentmanagement.webElements.svg.shapes.composite

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.compositeElements.AppGroupSvgElement
import contentmanagement.webElements.svg.shapes.BeShape
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeComposite
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

import scala.collection.mutable

abstract class BoxManualPositioning() extends BeShapeComposite {

  private val cache: mutable.HashMap[BeRenderingConfig, List[(BeShape, Point[Double], Dimension[Double])]] = mutable.HashMap()

  def calcOffsetsAndDimensions(config: BeRenderingConfig): List[(BeShape, Point[Double], Dimension[Double])]

  def getOffsetAndDimension(config: BeRenderingConfig): List[(BeShape, Point[Double], Dimension[Double])] = {
    if (cache.contains(config)) {
      cache(config)
    } else {
      val result = calcOffsetsAndDimensions(config)
      cache.put(config, result)
      result
    }
  }

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val renderedChildren = getOffsetAndDimension(rendererConfig).map((curShape, curRelOffset, curDim) => {
      val curShapeBounds = bounds.startPoint.moveWithDimension(curRelOffset.asDimension).withDimension(curDim)
      curShape.render(rendererConfig, curShapeBounds)
    })
    AppGroupSvgElement(renderedChildren)
  }

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = {
    val bounds = getOffsetAndDimension(rendererConfig).map((curShape, curRelOffset, curDim) => curRelOffset.withDimension(curDim))
    Dimension(bounds.map(_.endX).max, bounds.map(_.endY).max + rendererConfig.controlSegmentSize)
  }


}

