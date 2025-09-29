package contentmanagement.model.geometry

final case class Dimension[T: Numeric](width: T, height: T) {
  private val N = summon[Numeric[T]]
  import N.*

  lazy val area: T = width * height

  def ensureAtLeastAsBigAs(minDimension: Dimension[T]): Dimension[T] = {
    val useWidth = if (width < minDimension.width) minDimension.width else width
    val useHeight = if(height < minDimension.height) minDimension.height else height
    Dimension[T](useWidth, useHeight)
  }
  
  def increaseSize(addWidth: T, addHeight: T): Dimension[T] = Dimension[T](width + addWidth, height + addHeight)
  
  def increaseSize(other: Dimension[T]): Dimension[T] = Dimension[T](width + other.width, height + other.height)
  
}