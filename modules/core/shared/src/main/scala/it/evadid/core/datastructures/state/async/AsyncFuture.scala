package it.evadid.core.datastructures.state.async

import it.evadid.core.datastructures.state.observable.{ObservableValue, ObservableValueImpl}
import it.evadid.core.datastructures.state.async.AsyncDataState.*

import scala.concurrent.*
import scala.util.{Failure, Success}


case class AsyncFuture[F, S](underlying: Future[Either[FailureInfo[F], S]]) extends AsyncData[F, S] {

  override def map[S2](func: S => S2): AsyncData[F, S2] = AsyncFuture(underlying.map(_.map(func))(using ec))

  override def mapIfError[F2](func: F => F2): AsyncData[F2, S] = AsyncFuture(underlying.map(_.swap.map(_.map(func)).swap))

  override def mapAsync[S2](func: S => Future[S2]): AsyncData[F, S2] = {
    val res = Promise[Either[FailureInfo[F], S2]]()

    def fail(msg: String, cause: Throwable, data: Option[F]): Unit = {
      val err = Exception(msg, cause)
      res.success(Left(FailureInfo(err, data)))
    }

    withFinishedFirstState {
      case AsyncDataSuccess(data) => func(data).onComplete {
        case Success(value) => res.success(Right(value))
        case Failure(error) => fail("AsyncFuture failed because of future error: " + error.getMessage, error, None)
      }(using ExecutionContext.global)
      case AsyncDataFailed(err, data) => fail("AsyncFcn could not be executed because underlying value was error: " + err.msg, err, data)
    }

    AsyncFuture(res.future)
  }

  override val observeAllStates: ObservableValue[AsyncDataState[F, S]] = {
    val res = ObservableValueImpl[AsyncDataState[F, S]](Some(AsyncDataLoading[F, S]()))
    withFinishedFirstState(state => res.onNewValueArrived(Success(state)))
    res
  }

  override def stateNow(): AsyncDataState[F, S] = ???
}