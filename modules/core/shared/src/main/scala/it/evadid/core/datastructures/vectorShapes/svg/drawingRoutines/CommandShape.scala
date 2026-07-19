package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords

case class CommandShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T] {
  override def draw(builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] =
    builder.lineToRel(12, 0).lineToRel(6, 12).lineToRel(12, 0).lineToRel(6, -12).lineToRel(64, 0)
      .lineToRel(0, 100).lineToRel(-64, 0).lineToRel(-6, -12).lineToRel(-12, 0).lineToRel(-6, 12).lineToRel(-12, 0)
  override def onlyNonDistortedIfDimensionRatio: Option[Dimension[Double]] = None
}
