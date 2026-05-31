package todomove.webElementsOld.webElements.shapes.meta

import it.evadid.core.datastructures.geometry.Point

sealed trait PositionInformation[T: Fractional] {

  def setToOrMoveBy(newPos: Point[T]): PositionInformation[T]

}

case class PositionUnknown[T: Fractional]() extends PositionInformation[T] {
  def setToOrMoveBy(newPos: Point[T]): PositionInformation[T] = PositionIsOffset(newPos)
}

case class PositionIsOffset[T: Fractional](point: Point[T]) extends PositionInformation[T] {
  def setToOrMoveBy(newPos: Point[T]): PositionInformation[T] = PositionIsOffset(point.moveWithDimension(newPos.asDimension))
}
