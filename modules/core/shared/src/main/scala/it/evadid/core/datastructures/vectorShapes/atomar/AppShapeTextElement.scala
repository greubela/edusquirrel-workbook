package it.evadid.core.datastructures.vectorShapes.atomar

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppShapeAtomar
import it.evadid.core.datastructures.vectorShapes.config.AppShapeElementConfig
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent.MiddleCenter

case class AppShapeTextElement[T: Fractional](text: String, elementConfig: AppShapeElementConfig[T]) extends AppShapeAtomar[T] {

  override def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)] =
    Some(calculateMyRawDimension().aspectRatio(), MiddleCenter)

  override def calculateMyRawDimension(): Dimension[T] = {
    Dimension.fromDouble(elementConfig.font.measureText(text))
  }

}
