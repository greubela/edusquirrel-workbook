package it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine.DrawingRoutineRelativeToMaxDim
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderRelativeCoords

case class RectangleShape[T: Fractional]() extends DrawingRoutineRelativeToMaxDim[T]{

  override def draw(builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T] = ???

  override def onlyNonDistortedIfDimensionRatio: Option[Dimension[Double]] = ???
}
