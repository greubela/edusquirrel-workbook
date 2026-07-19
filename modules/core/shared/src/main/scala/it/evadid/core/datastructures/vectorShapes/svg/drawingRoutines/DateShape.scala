package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords

case class DateShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {
  override def draw(builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.quadraticBezierWithRel(10, 50, 0, 100).lineToRel(100, 0).quadraticBezierWithRel(-10, -50, 0, -100).lineToRel(-100, 0)
  override def onlyNonDistortedIfDimensionRatio: Option[Dimension[Double]] = None
}
