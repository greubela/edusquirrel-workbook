package it.evadid.core.datastructures.numbers

import scala.collection.immutable.HashSet


case class ValueDependentConstraints[I, T: Fractional](constraints: Set[I => NumberConstraint[T]]) {
  def addConstraint(constraint: I => NumberConstraint[T]): ValueDependentConstraints[I, T] = this.copy(constraints = constraints + constraint)

  def addConstraint(numberConstraint: NumberConstraint[T]): ValueDependentConstraints[I, T] = addConstraint(_ => numberConstraint)

  def getConstraint(input: I): NumberConstraint[T] = new NumberConstraint[T] {
    def minimum: Option[T] = constraints.map(_.apply(input)).flatMap(_.minimum).minOption

    def maximum: Option[T] = constraints.map(_.apply(input)).flatMap(_.maximum).maxOption
  }
}

object ValueDependentConstraints {

  def empty[I, T: Fractional](): ValueDependentConstraints[I, T] = ValueDependentConstraints[I, T](HashSet[I => NumberConstraint[T]]())

}
