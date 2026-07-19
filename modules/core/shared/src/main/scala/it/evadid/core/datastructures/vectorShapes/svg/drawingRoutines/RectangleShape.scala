package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension}
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords
import it.evadid.util.logging.Logger

case class RectangleShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T]{


  override def draw(logger: Logger, builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.lineToRel(100, 0).lineToRel(0, 100).lineToRel(-100, 0).lineToRel(0, -100)

  override def hasDesiredAspectRatio: Option[AspectRatio] = None
}
