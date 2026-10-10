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

object PositionUnknown {
  given [T: Fractional]: upickle.default.ReadWriter[PositionUnknown[T]] =
    upickle.default.readwriter[Unit].bimap(_ => (), _ => PositionUnknown[T]())
}

object PositionIsOffset {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[PositionIsOffset[T]] =
    upickle.default.readwriter[Point[T]].bimap(_.point, PositionIsOffset[T](_))
}

object PositionInformation {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[PositionInformation[T]] =
    upickle.default.readwriter[Option[Point[T]]].bimap(
      {
        case _: PositionUnknown[T] => None
        case value: PositionIsOffset[T] => Some(value.point)
      },
      {
        case None => PositionUnknown[T]()
        case Some(point) => PositionIsOffset(point)
      })
}
