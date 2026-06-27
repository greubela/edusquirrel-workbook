package it.evadid.core.datastructures.state.async

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.datastructures.state.observable.*
import it.evadid.distribution.command.SerializedException

import scala.concurrent.*
import scala.util.{Failure, Success, Try}

/*
  Basically a cleaned up Observable[AsyncDataState[]] that fails explicitly with its type and has syntactic sugar for values still loading / failed computations
 */

trait AsyncData[F, S] {

  protected given ec: ExecutionContext = ExecutionContext.global

  val observeAllStates: ObservableValue[AsyncDataState[F, S]]

  lazy val observeLoadedStates: ObservableValue[AsyncDataStateFinished[F, S]] = observeAllStates.deriveSome {
    case f: AsyncDataStateFinished[F, S] => Some(f)
    case _ => None
  }

  lazy val observeLoadedValues: ObservableValue[S] = observeAllStates.deriveSome {
    case s: AsyncDataSuccess[F, S] => Some(s.dataValue)
    case _ => None
  }

  lazy val futureFirstState: Future[AsyncDataStateFinished[F, S]] = observeLoadedStates.currentValueOrWaitForUpdate

  lazy val futureFirstValue: Future[S] = futureFirstState.map {
    case AsyncDataSuccess(value) => value
    case AsyncDataFailed(err, data) => throw new Exception("AsyncData::futureFirstValue failed because underlying value was error: " + err.msg + "( additional data: " + data + ")", err)
  }(using ec)

  protected def withFinishedFirstState(func: AsyncDataStateFinished[F, S] => Any): Unit = {
    futureFirstState.onComplete {
      case Success(value) => func(value)
      case Failure(exception) => {
        val err: Throwable = Exception("AsyncFuture failed because of future error: " + exception.getMessage, exception)
        func(AsyncDataFailed(SerializedException(err), None))
      }
    }(using ExecutionContext.global)
  }

  def map[S2](func: S => S2): AsyncData[F, S2] = AsyncState(observeAllStates.deriveValue(_.map(func)))

  def mapIfError[F2](func: F => F2): AsyncData[F2, S] = AsyncState(observeAllStates.deriveValue(_.mapIfError(func)))

  def combineIgnoreErrorData[F2, S2](other: AsyncData[F2, S2]): AsyncData[Nothing, (S, S2)] = {
    val res = State[AsyncDataState[Nothing, (S, S2)]](AsyncDataLoading())

    def failWithInfo(msg: String, cause: Throwable): Unit = {
      val err = Exception("error in AsyncData::combine: " + msg, cause)
      res.set(AsyncDataFailed(SerializedException(err), None))
    }

    val combined: ObservableValue[(AsyncDataState[F, S], AsyncDataState[F2, S2])] = this.observeAllStates.combineWith(other.observeAllStates)
    combined.addObserver(_.match {
      case (AsyncDataSuccess(s), AsyncDataSuccess(s2)) => res.set(AsyncDataSuccess((s, s2)))

      case (AsyncDataFailed(cause, data), _) => failWithInfo("Left Element of Combined has an Error", cause)
      case (_, AsyncDataFailed(cause, data)) => failWithInfo("Right Element of Combined has an Error", cause)
      case (AsyncDataLoading(), _) => res.set(AsyncDataLoading[Nothing, (S, S2)]())
      case (_, AsyncDataLoading()) => res.set(AsyncDataLoading[Nothing, (S, S2)]())
    })

    AsyncState(res.observable)
  }

  def combineAndMapIgnoreErrorData[F2, S2, O](other: AsyncData[F2, S2], mapFunc: (S, S2) => O): AsyncData[Nothing, O] = {
    combineIgnoreErrorData(other).map(tup => mapFunc(tup._1, tup._2))
  }


  def mapAsync[S2](func: S => Future[S2]): AsyncData[F, S2] = {
    val res: ObservableValueImpl[AsyncDataState[F, S2]] = ObservableValueImpl(Some(AsyncDataLoading[F, S2]()))

    def onNewBaseValueArrived(resTry: Try[S2]): Unit = resTry.match {
      case Success(value) => res.onNewValueArrived(Success(AsyncDataSuccess(value)))
      case Failure(error) => res.onNewValueArrived(Success(AsyncDataFailed(SerializedException(error), None)))
    }

    observeAllStates.addObserver {
      case AsyncDataSuccess(data) => func(data).onComplete(onNewBaseValueArrived)(using ec)
      case f: AsyncDataFailed[F, S] => {
        val exception = new Exception("Skipped Async Computation because required value was already an error: " + f.cause.msg)
        res.onNewValueArrived(Success(AsyncDataFailed(SerializedException(exception), f.additionalData)))
      }
      case l: AsyncDataLoading[F, S] => res.onNewValueArrived(Success(AsyncDataLoading[F, S2]()))
    }

    AsyncState(res)

  }

  def stateNow(): AsyncDataState[F, S]

}


object AsyncData {

  def forError[S](err: Throwable): AsyncValue[Nothing, S] = AsyncValue(AsyncDataFailed[Nothing, S](err))

  def forOption[S](option: Option[S]): AsyncValue[Nothing, S] = option.match {
    case Some(value) => AsyncValue(AsyncDataSuccess(value))
    case None => AsyncValue(AsyncDataFailed[Nothing, S](Exception("AsyncData::forOption with empty option")))
  }

  def forFuture[S](future: Future[S]): AsyncFuture[Nothing, S] = {
    val res: Future[Either[Nothing, S]] = future.map(Right(_))(using ExecutionContext.global)
    AsyncFuture[Nothing, S](res)
  }

  def forFutureWithFailureInfo[F, S](future: Future[Either[FailureInfo[F], S]]) = AsyncFuture[F, S](future)


}

