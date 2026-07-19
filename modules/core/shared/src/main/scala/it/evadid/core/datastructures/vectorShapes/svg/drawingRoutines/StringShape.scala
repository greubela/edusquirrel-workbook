package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords

case class StringShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {
  override def draw(builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.moveToRel(10, 0).lineToRel(0, 25).lineToRel(-10, 0).lineToRel(0, 50).lineToRel(10, 0).lineToRel(0, 25)
      .lineToRel(80, 0).lineToRel(0, -25).lineToRel(10, 0).lineToRel(0, -50).lineToRel(-10, 0).lineToRel(0, -25)
  override def onlyNonDistortedIfDimensionRatio: Option[Dimension[Double]] = None
}
