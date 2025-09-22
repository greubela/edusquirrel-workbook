package contentmanagement.model

import contentmanagement.model.bounds.TransformBounds


case class Bounds(startX: Double, startY: Double, endX: Double, endY: Double) {

  val width: Double = endX - startX

  val height: Double = endY - startY

  val centerX: Double = startX + width / 2

  val centerY: Double = startY + height / 2

  lazy val ratio: Double = width / height

  def scaledToAbsolute(scaleWidth: Double, scaleHeight: Double): Bounds = Bounds(startX * scaleWidth, startY * scaleHeight, endX * scaleWidth, endY * scaleHeight)

  def transformBounds(t: TransformBounds): Bounds = t.getTransformed(this)

}

object Bounds {

  /*
  def scaledBounds(bounds: Bounds, scaleOrigin: Orientation, newWidth: Double, newHeight: Double): Bounds = {
    val diffX = newWidth - bounds.width
    val diffY = newHeight - bounds.height

    scaleOrigin match {
      case CENTER => Bounds.fromRectangle(bounds.startX - diffX / 2, bounds.startY - diffY / 2, newWidth, newHeight)
      case TOP_LEFT => Bounds.fromRectangle(bounds.startX, bounds.startY, newWidth, newHeight)
    }
  }*/

  def translatedBounds(bounds: Bounds, dx: Double, dy: Double): Bounds = Bounds.fromRectangle(bounds.startX - dx, bounds.startY - dy, bounds.width, bounds.height)

  def fromCenter(centerX: Double, centerY: Double, width: Double, height: Double): Bounds = Bounds(
    centerX - width / 2,
    centerY - height / 2,
    centerX + width / 2,
    centerY + height / 2
  )

  def fromRectangle(startX: Double, startY: Double, width: Double, height: Double): Bounds = Bounds(
    startX,
    startY,
    startX + width,
    startY + height
  )

  def fromPoints(startX: Double, startY: Double, endX: Double, endY: Double): Bounds = Bounds(startX, startY, endX, endY)

  def wholeImage(): Bounds = Bounds(0, 0, 1, 1)

}




