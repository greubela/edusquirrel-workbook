package it.evadid.core.datastructures.state.async

import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.datastructures.state.observable.{ConstantValueObservable, ObservableValue}

import scala.util.Try

case class AsyncValue[F, S](underlyingState: AsyncDataStateFinished[F, S]) extends AsyncData[F, S] {

  override lazy val observeAllStates: ObservableValue[AsyncDataState[F, S]] = ConstantValueObservable(underlyingState)

  override def stateNow(): AsyncDataState[F, S] = underlyingState
}

object AsyncValue {

  def apply[S](value: S): AsyncValue[Nothing, S] = AsyncValue(AsyncDataSuccess(value))

  def apply[S](error: Throwable): AsyncValue[Nothing, S] = AsyncValue(AsyncDataFailed[Nothing, S](error, None))

  def apply[S](tryValue: Try[S]): AsyncValue[Nothing, S] = tryValue.toEither.match {
    case Left(err) => apply(err)
    case Right(value) => apply(value)
  }

  def apply[S](option: Option[S]): AsyncValue[Nothing, S] = option.match{
    case Some(value) => apply(value)
    case None => apply(Exception("AsyncValue::apply created with empty option"))
  }

}

