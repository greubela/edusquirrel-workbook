package it.evadid.core.datastructures.vectorShapes.atomar

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppShapeAtomar
import it.evadid.core.datastructures.vectorShapes.config.AppShapeElementConfig
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent.MiddleCenter

/** How text is drawn into its bounds. */
enum TextPlacement derives upickle.default.ReadWriter {
  /** Top-left aligned and scaled with its bounds, keeping its aspect ratio. */
  case FitToBounds
  /** Centered in its bounds at the font's own size. */
  case Centered
}

case class AppShapeTextElement[T: Fractional](text: String, elementConfig: AppShapeElementConfig[T],
                                              placement: TextPlacement = TextPlacement.FitToBounds) extends AppShapeAtomar[T] {

  override def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)] = {
    val size = calculateMyRawDimension().toDouble
    Option.when(placement == TextPlacement.FitToBounds && size.width > 0 && size.height > 0)(size.aspectRatio() -> MiddleCenter)
  }

  override def calculateMyRawDimension(): Dimension[T] = {
    Dimension.fromDouble(elementConfig.font.measureText(text))
  }

}
