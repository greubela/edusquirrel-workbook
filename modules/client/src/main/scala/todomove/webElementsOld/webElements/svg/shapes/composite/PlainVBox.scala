package todomove.webElementsOld.webElements.svg.shapes.composite

import it.evadid.core.datastructures.geometry.Point
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.shapes.BeShape

case class PlainVBox(shapes: List[BeShape]) extends BoxManualPositioning {

  override def calcOffsetsAndDimensions(renderingInfo: BeRenderingConfig): List[ManualPositionElement] = {
    val dims = shapes.map(_.displaySize(renderingInfo))
    val offsetsY = dims.indices.map(curDimNr => dims.slice(0, curDimNr).map(_.height).sum)
    shapes.zip(offsetsY).zip(dims).map(trip => {
      ManualPositionElement(trip._1._1, Point[Double](0, trip._1._2), trip._2)
    })

  }
}
