package it.evadid.core.datastructures.numbers

import upickle.default.*

trait NumberConstraint[T: Fractional] {
  def minimum: Option[T]

  def maximum: Option[T]
}

case class NumberConstraintImpl[T: Fractional](override val minimum: Option[T], override val maximum: Option[T]) extends NumberConstraint[T]


object NumberConstraintImpl {
  given [T: Fractional: ReadWriter]: ReadWriter[NumberConstraintImpl[T]] =
    readwriter[(Option[T], Option[T])].bimap(c => (c.minimum, c.maximum), p => NumberConstraintImpl(p._1, p._2))
}
