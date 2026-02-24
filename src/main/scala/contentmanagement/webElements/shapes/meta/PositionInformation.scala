package contentmanagement.webElements.shapes.meta

import contentmanagement.model.geometry.*

sealed trait PositionInformation[T: Fractional] {

  def setToOrMoveBy(newPos: Point[T]): PositionInformation[T]

}

case class PositionUnknown[T: Fractional]() extends PositionInformation[T] {
  def setToOrMoveBy(newPos: Point[T]): PositionInformation[T] = PositionIsOffset(newPos)
}

case class PositionIsOffset[T: Fractional](point: Point[T]) extends PositionInformation[T] {
  def setToOrMoveBy(newPos: Point[T]): PositionInformation[T] = PositionIsOffset(point.moveWithDimension(newPos.asDimension))
}
