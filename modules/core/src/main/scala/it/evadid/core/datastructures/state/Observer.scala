package it.evadid.core.datastructures.state

case class Observer[T](val handleOnUpdate: T => Any, val handleOnError: Throwable => Any, executionMethod: ExecutionMethod) {

}