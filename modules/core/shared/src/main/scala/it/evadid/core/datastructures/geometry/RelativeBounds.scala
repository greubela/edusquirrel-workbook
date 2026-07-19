package it.evadid.core.datastructures.geometry

case class RelativeBounds[T: Fractional](offsetInParents: Point[T], dimension: Dimension[T]) {
  def toAbsoluteBounds(absoluteStartingPointParents: Point[T]): Bounds[T] = Bounds(absoluteStartingPointParents + offsetInParents, dimension)
}
