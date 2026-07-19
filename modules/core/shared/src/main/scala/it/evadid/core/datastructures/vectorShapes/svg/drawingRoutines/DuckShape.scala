package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords

case class DuckShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {

  override def onlyNonDistortedIfDimensionRatio: Option[Dimension[Double]] = Some(Dimension[Double](125, 50))

  override def draw(builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] = {

    builder
      .moveToRel(15, 25)

      .cubicBezierToRel(-6, 0, -7, 0, -10, -5)
      .cubicBezierToRel(-1, -1, -1, -1, -2, -1)
      .cubicBezierToRel(-1, 0, -1, 0, -2, 1)
      .cubicBezierToRel(-3, 6, -1, 27, 14, 30)

      .lineToRel(100 - 15 + 35, 0) // to the right.

      // right elements
      .lineToRel(20, 0)

      .cubicBezierToRel(11, 0, 20, -12, 8, -21)
      .cubicBezierToRel(-2, -2, -5, -5, -3, -8)
      .cubicBezierToRel(1, -1, 3, -1, 5, -1)

      .cubicBezierToRel(2, 0, 4, -1, 2, -3)
      .cubicBezierToRel(2, -2, 4, -5, 0, -5)

      .cubicBezierToRel(-3, 0, -5, 0, -5, -3)
      .cubicBezierToRel(0, -3, -5, -9, -12, -9)
      .cubicBezierToRel(-10, 0, -12, 10, -10, 15)
      .cubicBezierToRel(2, 4, 5, 10, -5, 10)
  }


}
