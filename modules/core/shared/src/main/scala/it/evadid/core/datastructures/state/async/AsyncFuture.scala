package it.evadid.core.datastructures.state.async

import it.evadid.distribution.command.SerializedException
import it.evadid.core.datastructures.state.observable.{ObservableValue, ObservableValueImpl}
import it.evadid.core.datastructures.state.async.AsyncDataState.*

import scala.concurrent.*
import scala.util.{Success, Try}
import scala.util.control.NonFatal


case class AsyncFuture[F, S](underlying: Future[Either[FailureInfo[F], S]]) extends AsyncData[F, S] {

  override def map[S2](func: S => S2): AsyncData[F, S2] = AsyncFuture(underlying.map(_.map(func)))

  override def mapIfError[F2](func: F => F2): AsyncData[F2, S] = AsyncFuture(underlying.map(_.swap.map(_.map(func)).swap))

  override def mapAsync[S2](func: S => Future[S2]): AsyncData[F, S2] = {
    val mapped: Future[Either[FailureInfo[F], S2]] = futureFirstState.flatMap {
      case AsyncDataSuccess(data) =>
        Try(func(data)).fold(Future.failed, identity).map(value => Right(value)).recover {
          case NonFatal(error) => Left(FailureInfo(Exception("AsyncFuture failed because of future error: " + error.getMessage, error), None))
        }
      case AsyncDataFailed(error, data) =>
        Future.successful(Left(FailureInfo(Exception("AsyncFunction could not be executed because underlying value was error: " + error.msg, error), data)))
    }
    AsyncFuture(mapped)
  }

  val futureFirstState: Future[AsyncDataStateFinished[F, S]] = underlying.map {
    case Left(failureInfo) => AsyncDataFailed[F, S](failureInfo.error, failureInfo.data)
    case Right(value) => AsyncDataSuccess[F, S](value)
  }(using ExecutionContext.global).recover {
    case error => AsyncDataFailed[F, S](error, None)
  }(using ExecutionContext.global)

  override lazy val observeAllStates: ObservableValue[AsyncDataState[F, S]] = {
    val res = ObservableValueImpl[AsyncDataState[F, S]](Some(AsyncDataLoading[F, S]()))
    withFinishedFirstState(state => res.onNewValueArrived(Success(state)))
    res
  }

  override def stateNow(): AsyncDataState[F, S] = observeAllStates.now().getOrElse(AsyncDataLoading[F, S]())
}