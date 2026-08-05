package it.evadid.core.datastructures.state.async

import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.datastructures.state.observable.ObservableValue

import scala.concurrent.*
import scala.util.Try

implicit class AsyncState[F, S](underlying: ObservableValue[AsyncDataState[F, S]]) extends AsyncData[F, S] {


  /*private val futureFirstState: Future[AsyncDataStateFinished[F, S]] = {
    val res: Promise[AsyncDataStateFinished[F, S]] = Promise[AsyncDataStateFinished[F, S]]()
    val subscription: Subscription[AsyncDataState[F, S]] = underlying.addObserver(_.match {
      case newState: AsyncDataStateFinished[F, S] => {
        res.success(newState)
        subscription.cancel()
      }
      case _ => {
      }
    })
    res.future
  }*/

  override def stateNow(): AsyncDataState[F, S] = observeAllStates.now().getOrElse(AsyncDataLoading[F, S]())

  override lazy val observeAllStates: ObservableValue[AsyncDataState[F, S]] = underlying
  //= observeLoadedStates.currentValueOrWaitForUpdate

  val futureFirstState: Future[AsyncDataStateFinished[F, S]] = {
    val res: Promise[AsyncDataStateFinished[F, S]] = Promise()
    observeAllStates.addObserver(onNext => if (!res.isCompleted && !onNext.isLoading) {
      onNext.match {
        case a@AsyncDataSuccess(value) => res.complete(Try(a))
        case AsyncDataFailed(error, additionalData) => res.failure(error)
        case AsyncDataLoading() =>
      }
    })
    res.future
  }

}
