package contentmanagement.model.geometry

import contentmanagement.model.geometry.*

import scala.math.Fractional.Implicits.infixFractionalOps

case class Bounds[T: Fractional](startPoint: Point[T], dimension: Dimension[T]) {

  private val N = summon[Fractional[T]]

  import N.*

  def cornerPoints: Seq[Point[T]] = List(
    Point[T](startX, startY),
    Point[T](startX, endY),
    Point[T](endX, startY),
    Point[T](endX, endY)
  )

  def endPoint = Point[T](startX + width, startY + height)

  def centerPoint = Point[T](startX + width / fromInt(2), startY + height / fromInt(2))

  def startX: T = startPoint.x

  def startY: T = startPoint.y

  def width: T = dimension.width

  def height: T = dimension.height

  def area: T = width * height

  def areaString: String = width.toString + " x " + height.toString

  def centerX = centerPoint.x

  def centerY = centerPoint.y

  def endX = endPoint.x

  def endY = endPoint.y

  def ensureAtLeastAsBigAs(minDim: Dimension[T]): Bounds[T] =
    Bounds[T](startPoint, dimension.ensureAtLeastAsBigAs(minDim))

}


object Bounds {

  def fromCenter[T: Fractional](centerPoint: Point[T], dim: Dimension[T]) = {
    val N = summon[Fractional[T]]
    import N.*
    val startPoint = centerPoint - Point[T](dim.width / fromInt(2), dim.height / fromInt(2))
    Bounds[T](startPoint, dim)
  }

  def fromPoints[T: Fractional](startPoint: Point[T], endPoint: Point[T]): Bounds[T] = {
    Bounds[T](startPoint, startPoint.dimensionBetweenThisAnd(endPoint))
  }

  def thatContainsAll[T: Fractional](points: List[Point[T]]): Bounds[T] = {
    val minX: T = points.minBy(_.x).x
    val minY: T = points.minBy(_.y).y
    val maxX: T = points.maxBy(_.x).x
    val maxY: T = points.maxBy(_.y).y
    fromPoints(Point[T](minX, minY), Point[T](maxX, maxY))
  }

}




