package contentmanagement.model.bounds

import contentmanagement.model.Bounds

case class CombinedBoundTransformation(boundTransformations: List[TransformBounds]) extends TransformBounds {

  override def preservesRatio: Boolean = !boundTransformations.map(_.preservesRatio).contains(false)

  override def getTransformed(in: Bounds): Bounds = boundTransformations.foldLeft(in) { (currentBounds, currentTransformation) => currentTransformation.getTransformed(currentBounds)
  }
}