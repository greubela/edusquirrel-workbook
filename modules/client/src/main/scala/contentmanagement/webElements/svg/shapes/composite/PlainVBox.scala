package contentmanagement.webElements.svg.shapes.composite

import contentmanagement.webElements.svg.shapes.BeShape
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.geometry.Point

case class PlainVBox(shapes: List[BeShape]) extends BoxManualPositioning {

  override def calcOffsetsAndDimensions(renderingInfo: BeRenderingConfig): List[ManualPositionElement] = {
    val dims = shapes.map(_.displaySize(renderingInfo))
    val offsetsY = dims.indices.map(curDimNr => dims.slice(0, curDimNr).map(_.height).sum)
    shapes.zip(offsetsY).zip(dims).map(trip => {
      ManualPositionElement(trip._1._1, Point[Double](0, trip._1._2), trip._2)
    })

  }
}
