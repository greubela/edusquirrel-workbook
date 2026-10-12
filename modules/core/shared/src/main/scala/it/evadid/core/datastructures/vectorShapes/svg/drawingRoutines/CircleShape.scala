package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension}
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords
import it.evadid.util.logging.Logger

case class CircleShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {

  override def draw(logger: Logger, builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =     builder.
    moveToRel(0, 50)
    .arcToRel(50, 50, 100, 0, 180, true, false)
    .arcToRel(50, 50, -100, 0, 180, true, false)

  override def hasDesiredAspectRatio: Option[AspectRatio] = Some(AspectRatio(1,1))
}

object CircleShape {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[CircleShape[T]] =
    upickle.default.readwriter[Unit].bimap(value => (), _ => CircleShape[T]())
}
