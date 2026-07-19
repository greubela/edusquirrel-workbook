package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension}
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords
import it.evadid.util.logging.Logger

case class StringShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {
  override def draw(logger: Logger, builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.moveToRel(10, 0).lineToRel(0, 25).lineToRel(-10, 0).lineToRel(0, 50).lineToRel(10, 0).lineToRel(0, 25)
      .lineToRel(80, 0).lineToRel(0, -25).lineToRel(10, 0).lineToRel(0, -50).lineToRel(-10, 0).lineToRel(0, -25)

  override def hasDesiredAspectRatio: Option[AspectRatio] = None
}
