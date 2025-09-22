package contentmanagement.model.bounds

import contentmanagement.model.Bounds

trait AdjustSizeTransformations extends TransformBounds{

}

object AdjustSizeTransformations {
  
  case class ScaleBounds(scale: Double) extends TransformBounds {
    val preservesRatio: Boolean = true

    override def getTransformed(in: Bounds): Bounds = in.scaledToAbsolute(scale, scale)
  }

  case class SCALE_HEIGHT(destWidth: Double) extends TransformBounds {

    val preservesRatio: Boolean = true

    override def getTransformed(in: Bounds): Bounds = {
      val scale = destWidth / in.width
      in.scaledToAbsolute(scale, scale)
    }
  }

  case class ScaleWidth(destHeight: Double) extends TransformBounds {

    val preservesRatio: Boolean = true

    override def getTransformed(in: Bounds): Bounds = {
      val scale = destHeight / in.height
      in.scaledToAbsolute(scale, scale)
    }
  }
  
}