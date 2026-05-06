package it.evadid.core.datastructures.state

import scala.util.Try

case class Observer[T](val handleOnUpdate: Try[T] => Any, executionMethod: ExecutionMethod, executionPriority: Int) {

}