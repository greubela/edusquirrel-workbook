package it.evadid.core.datastructures.state.async

import it.evadid.distribution.command.SerializedException
import it.evadid.core.datastructures.state.async.AsyncDataState.*

case class FailureInfo[F](error: SerializedException, data: Option[F]) {
  def map[F2](func: F => F2): FailureInfo[F2] = FailureInfo(error, data.map(func))
}

/*

sealed trait FailureInfo{ // todo proper type
  def toTyped(): FailureInfoTyped[T]
}

case class FailureAfterSuccess(err: Throwable, priorSuccess: Executioninfo)

case class FaiureInfoUntyped(err: Throwable, data: Map[String, String]) extends FailureInfo{

}

case class SerializedFailureInfo[]
 */

object FailureInfo {
  def apply(msg: String): FailureInfo[Nothing] = FailureInfo(Exception(msg))

  def apply(err: Throwable): FailureInfo[Nothing] = FailureInfo(SerializedException(err), None)

  def apply[F](err: Throwable, data: F): FailureInfo[F] = FailureInfo(err, Some(data))

  def apply[F](err: Throwable, data: Option[F]): FailureInfo[F] = FailureInfo(SerializedException(err), data)

  def apply(error: SerializedException): FailureInfo[Nothing] = FailureInfo(error, None)
}


