package it.evadid.core.datastructures.state.async

import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.datastructures.state.observable.ObservableValue

import scala.concurrent.*

implicit class AsyncState[F, S](underlying: ObservableValue[AsyncDataState[F, S]]) extends AsyncData[F, S] {
  override def stateNow(): AsyncDataState[F, S] = observeAllStates.now().getOrElse(AsyncDataLoading[F, S]())

  override lazy val observeAllStates: ObservableValue[AsyncDataState[F, S]] = underlying

  val futureFirstState: Future[AsyncDataStateFinished[F, S]] = {
    val result = Promise[AsyncDataStateFinished[F, S]]()
    val subscription = observeAllStates.addObserver {
      case finished: AsyncDataStateFinished[F, S] => result.trySuccess(finished)
      case _: AsyncDataLoading[F, S] => ()
    }
    // Register cleanup after addObserver returns: it may replay a finished value synchronously.
    result.future.onComplete(_ => subscription.cancel())(using ExecutionContext.parasitic)
    result.future
  }

}
