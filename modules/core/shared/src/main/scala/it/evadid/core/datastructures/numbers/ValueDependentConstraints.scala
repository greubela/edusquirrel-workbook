package it.evadid.core.datastructures.numbers

import scala.collection.immutable.HashSet


case class ValueDependentConstraints[I, T: Fractional](constraints: Set[I => NumberConstraint[T]]) {
  def addConstraint(constraint: I => NumberConstraint[T]): ValueDependentConstraints[I, T] = this.copy(constraints = constraints + constraint)

  def addConstraint(numberConstraint: NumberConstraint[T]): ValueDependentConstraints[I, T] = addConstraint(_ => numberConstraint)

  /** Snapshot the intersection of all bounds for this input. */
  def getConstraint(input: I): NumberConstraint[T] = {
    val evaluated = constraints.iterator.map(_.apply(input)).toList
    NumberConstraintImpl(evaluated.flatMap(_.minimum).maxOption, evaluated.flatMap(_.maximum).minOption)
  }

}

object ValueDependentConstraints {

  def empty[I, T: Fractional](): ValueDependentConstraints[I, T] = ValueDependentConstraints[I, T](HashSet[I => NumberConstraint[T]]())

}
