package contentmanagement.model.bounds

import contentmanagement.model.Bounds

sealed trait SameSizeTransformation extends TransformBounds {

}

object SameSizeTransformation {
  case class CenteredWithOriginalSizeTransformation(imageWidth: Double, imageHeight: Double) extends TransformBounds {
    val preservesRatio: Boolean = true
    override def getTransformed(input: Bounds): Bounds = {
      Bounds.fromCenter(input.centerX, input.centerY, imageWidth, imageHeight)
    }
  }

  case class TranslateTransformation(dx: Double, dy: Double) extends TransformBounds {
    val preservesRatio: Boolean = true

    override def getTransformed(in: Bounds): Bounds = Bounds.translatedBounds(in, dx, dy)
  }
}
