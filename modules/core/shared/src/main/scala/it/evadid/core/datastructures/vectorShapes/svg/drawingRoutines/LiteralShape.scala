package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension}
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords
import it.evadid.util.logging.Logger

case class LiteralShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {

  override def draw(logger: Logger, builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.moveToRel(0, 50).lineToRel(50, -50).lineToRel(40, 0).lineToRel(10, 10).lineToRel(0, 80)
      .lineToRel(-10, 10).lineToRel(-40, 0).lineToRel(-50, -50)

  override def hasDesiredAspectRatio: Option[AspectRatio] = None
}
