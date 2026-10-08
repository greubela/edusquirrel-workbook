package it.evadid.core.datastructures.geometry

import upickle.default.*

case class RelativeBounds[T: Fractional](offsetInParents: Point[T], dimension: Dimension[T]) {
  def toAbsoluteBounds(absoluteStartingPointParents: Point[T]): Bounds[T] = Bounds(absoluteStartingPointParents + offsetInParents, dimension)
}


object RelativeBounds {
  given [T: Fractional: ReadWriter]: ReadWriter[RelativeBounds[T]] = readwriter[(Point[T], Dimension[T])].bimap(
    b => (b.offsetInParents, b.dimension), pair => RelativeBounds(pair._1, pair._2)
  )
}
