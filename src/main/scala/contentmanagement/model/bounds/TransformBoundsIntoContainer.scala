package contentmanagement.model.bounds

import contentmanagement.model.Bounds

sealed trait TransformBoundsIntoContainer extends TransformBounds {
  def containerBounds: Bounds
}

object TransformBoundsIntoContainer {
  case class IGNORE_RATIO_FIT_BOUNDS(containerBounds: Bounds) extends TransformBoundsIntoContainer {
    val preservesRatio = false

    override def getTransformed(in: Bounds): Bounds = containerBounds
  }

  case class PRESERVE_RATIO_FIT_WIDTH(containerBounds: Bounds) extends TransformBoundsIntoContainer {

    val preservesRatio: Boolean = true

    override def getTransformed(input: Bounds): Bounds = {
      def scalingFactor = containerBounds.width / input.width

      val finalWidth = containerBounds.width
      val finalHeight = input.height * scalingFactor
      Bounds.fromCenter(containerBounds.centerX, containerBounds.centerY, finalWidth, finalHeight)
    }
  }


  case class PRESERVE_RATIO_FIT_HEIGHT(containerBounds: Bounds) extends TransformBoundsIntoContainer {

    val preservesRatio: Boolean = true

    override def getTransformed(input: Bounds): Bounds = {
      def scalingFactor = containerBounds.height / input.height

      val finalHeight = containerBounds.height
      val finalWidth = input.width * scalingFactor

      Bounds.fromCenter(containerBounds.centerX, containerBounds.centerY, finalWidth, finalHeight)
    }
  }

  case class PRESERVE_RATIO_NOTHING_EMPTY(containerBounds: Bounds) extends TransformBoundsIntoContainer {

    val preservesRatio: Boolean = true

    private val fitToHeight = PRESERVE_RATIO_FIT_HEIGHT(containerBounds)
    private val fitToWidth = PRESERVE_RATIO_FIT_WIDTH(containerBounds)

    override def getTransformed(input: Bounds): Bounds = {
      val fittedToHeight = fitToHeight.getTransformed(input)
      if (fittedToHeight.width >= containerBounds.width) {
        fittedToHeight
      } else {
        fitToWidth.getTransformed(input)
      }
    }
  }

  case class PRESERVE_RATIO_FIT_BOUNDS(containerBounds: Bounds) extends TransformBoundsIntoContainer {

    val preservesRatio: Boolean = true

    private val fitToHeight = PRESERVE_RATIO_FIT_HEIGHT(containerBounds)
    private val fitToWidth = PRESERVE_RATIO_FIT_WIDTH(containerBounds)

    override def getTransformed(input: Bounds): Bounds = {
      val fittedToHeight = fitToHeight.getTransformed(input)
      if (fittedToHeight.width <= containerBounds.width) {
        fittedToHeight
      } else {
        fitToWidth.getTransformed(input)
      }
    }
  }

}