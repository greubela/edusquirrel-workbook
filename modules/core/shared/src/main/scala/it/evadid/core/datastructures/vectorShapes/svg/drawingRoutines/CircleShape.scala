package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords

case class CircleShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim {

  override def draw(builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] = {
    builder.
      moveToRel(0, 50)
      .arcToRel(50, 50, 100, 50, 180, true, false)
      .arcToRel(50, 50, 100, 50, 180, false, false)
  }

  override def onlyNonDistortedIfDimensionRatio: Option[Dimension[Double]] = Some(Dimension(100, 100))
}
