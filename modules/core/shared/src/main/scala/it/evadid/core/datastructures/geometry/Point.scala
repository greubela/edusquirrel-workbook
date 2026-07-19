package it.evadid.core.datastructures.geometry

final case class Point[T: Fractional](x: T, y: T) {
  private val N = summon[Fractional[T]]

  import N.* // brings +, -, *, etc. as extension ops

  lazy val toDouble = Point[Double](x.toDouble, y.toDouble)
  lazy val asDimension: Dimension[T] = new Dimension[T](x, y)

  def +(that: Point[T]): Point[T] = Point(x + that.x, y + that.y)

  def -(that: Point[T]): Point[T] = Point(x - that.x, y - that.y)

  def dimensionBetweenThisAnd(other: Point[T]): Dimension[T] = {
    Dimension[T](other.x - this.x, other.y - this.y)
  }

  def withDimension(dimension: Dimension[T]): Bounds[T] = Bounds[T](this, dimension)

  def moveWithDimension(dimension: Dimension[T]): Point[T] = Point[T](x + dimension.width, y + dimension.height)

  def distanceToSquared(other: Point[T]): T = {
    val dx = x - other.x
    val dy = y - other.y
    dx * dx + dy * dy
  }

}

object Point {


  def intToT[T: Fractional](intValue: Int): T = {
    val N = summon[Fractional[T]]
    import N.*
    fromInt(intValue)
  }

  def doubleToT[T: Fractional](doubleValue: Double, divideBy: Int = 1, maxDiv: Int = 10000000): T = {
    val N = summon[Fractional[T]]
    import N.*
    N.parseString(doubleValue.toString).getOrElse {
      val scale = math.min(maxDiv, 1000000)
      fromInt((doubleValue * scale).round.toInt) / fromInt(scale)
    }
  }

}
