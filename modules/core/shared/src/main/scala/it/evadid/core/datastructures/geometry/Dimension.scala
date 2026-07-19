package it.evadid.core.datastructures.geometry


final case class Dimension[T: Fractional](width: T, height: T) {
  private val N = summon[Fractional[T]]

  import N.*

  lazy val area: T = width * height

  lazy val toDouble: Dimension[Double] = Dimension(width.toDouble, height.toDouble)

  def aspectRatio(): AspectRatio = AspectRatio(width.toDouble, height.toDouble)

  def scaled(factor: T): Dimension[T] = Dimension[T](width * factor, height * factor)

  def scaledToFitInto(other: Dimension[T]): Dimension[T] = {
    val maxUpscaleX = other.width / width
    val maxUpscaleY = other.height / height
    scaled(min(maxUpscaleX, maxUpscaleY))
  }

  def ensureAtLeastAsBigAs(minDim: Dimension[T]): Dimension[T] = ensureAtLeastAsBigAs(minDim.width, minDim.height)

  def ensureAtLeastAsBigAs(minWidth: T, minHeight: T): Dimension[T] =
    Dimension[T](if (width < minWidth) minWidth else width, if (height < minHeight) minHeight else height)

  def ensureWidth(minWidth: T): Dimension[T] =
    Dimension[T](if (width < minWidth) minWidth else width, height)

  def ensureHeight(minHeight: T): Dimension[T] =
    Dimension[T](width, if (height < minHeight) minHeight else height)

  def increaseSize(addWidth: T, addHeight: T): Dimension[T] = Dimension[T](width + addWidth, height + addHeight)

  def increaseSize(other: Dimension[T]): Dimension[T] = Dimension[T](width + other.width, height + other.height)

  def decreaseSize(other: Dimension[T]): Dimension[T] = Dimension[T](width - other.width, height - other.height)

  def asPoint: Point[T] = new Point[T](width, height)

  def withOffset(relativeOffset: Point[T]): RelativeBounds[T] = RelativeBounds(relativeOffset, this)

  lazy val withoutOffset: RelativeBounds[T] = RelativeBounds(Point.fromIntPoint(0, 0), this)
}

object Dimension {

  def fromInt[T: Fractional](intDim: Dimension[Int]): Dimension[T] = {
    Dimension[T](Point.doubleToT(intDim.width), Point.doubleToT(intDim.height))
  }

  def fromDouble[T: Fractional](doubleDim: Dimension[Double]): Dimension[T] = {
    Dimension[T](Point.doubleToT(doubleDim.width), Point.doubleToT(doubleDim.height))
  }

  def fromRatioAndMaxDimension[T: Fractional](aspectRatio: AspectRatio, mustFitInto: Dimension[T]): Dimension[T] = {
    val ratioAsT = Point.doubleToT(aspectRatio.widthToHeight)
    Dimension[T](ratioAsT, Point.doubleToT(1)).scaledToFitInto(mustFitInto)
  }

}