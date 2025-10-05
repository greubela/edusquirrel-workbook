package contentmanagement.model.geometry

final case class Point[T: Fractional](x: T, y: T) {
  private val N = summon[Fractional[T]]

  import N.* // brings +, -, *, etc. as extension ops

  def +(that: Point[T]): Point[T] = Point(x + that.x, y + that.y)

  def -(that: Point[T]): Point[T] = Point(x - that.x, y - that.y)

  def dimensionBetweenThisAnd(other: Point[T]): Dimension[T] = {
    Dimension[T](other.x - this.x, other.y - this.y)
  }

  def withDimension(dimension: Dimension[T]): Bounds[T] = Bounds[T](this, dimension)

  def moveWithDimension(dimension: Dimension[T]): Point[T] = Point[T](x + dimension.width, y + dimension.height)


}

