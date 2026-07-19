package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension}
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords
import it.evadid.util.logging.Logger

case class NumericShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {

  override def draw(logger: Logger, builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.moveToRel(10, 0).quadraticBezierWithRel(-20, 50, 0, 100).lineToRel(80, 0).quadraticBezierWithRel(20, -50, 0, -100)

  override def hasDesiredAspectRatio: Option[AspectRatio] = None
}
