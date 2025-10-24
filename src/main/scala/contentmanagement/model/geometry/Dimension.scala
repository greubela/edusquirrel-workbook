package contentmanagement.model.geometry

final case class Dimension[T: Fractional](width: T, height: T) {
  private val N = summon[Fractional[T]]

  import N.*

  lazy val area: T = width * height

  lazy val toDouble: Dimension[Double] = Dimension(width.toDouble, height.toDouble)
  
  def ensureAtLeastAsBigAs(minDimension: Dimension[T]): Dimension[T] = {
    val useWidth = if (width < minDimension.width) minDimension.width else width
    val useHeight = if (height < minDimension.height) minDimension.height else height
    Dimension[T](useWidth, useHeight)
  }

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

}