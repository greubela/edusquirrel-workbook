package it.evadid.core.datastructures.vectorShapes.atomar

import it.evadid.core.datastructures.geometry.{AspectRatio, Bounds, Dimension}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppShapeAtomar
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine
import it.evadid.core.datastructures.vectorShapes.config.AppShapeElementConfig
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath
import it.evadid.util.logging.Logger

case class AppShapeDrawingRoutineElement[T: Fractional]
(
  routine: DrawingRoutine[T],
  config: AppShapeElementConfig[T],
  minSize: Option[Dimension[T]] = None,
) extends AppShapeAtomar[T] {

  override def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)] = {
    routine.hasDesiredAspectRatio.map((_, AlignmentInParent.MiddleCenter))
  }

  override def calculateMyRawDimension(): Dimension[T] = {
    summon[Fractional[Double]]
    if (minSize.nonEmpty) minSize.get else Dimension.fromDouble[T](Dimension[Double](0, 0))
  }

  override def elementConfig: AppShapeElementConfig[T] = config

  def renderPath(logger: Logger, bounds: Bounds[T]): SvgPath = routine.renderPath(logger, bounds)
}
