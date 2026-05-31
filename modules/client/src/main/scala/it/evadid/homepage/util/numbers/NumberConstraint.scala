package it.evadid.homepage.util.numbers

trait NumberConstraint[T: Fractional] {
  def minimum: Option[T]

  def maximum: Option[T]
}

case class NumberConstraintImpl[T: Fractional](override val minimum: Option[T], override val maximum: Option[T]) extends NumberConstraint[T]
