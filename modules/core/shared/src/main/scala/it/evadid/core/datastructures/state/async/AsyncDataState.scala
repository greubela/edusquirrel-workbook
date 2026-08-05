package it.evadid.core.datastructures.state.async

import it.evadid.distribution.command.SerializedException
import it.evadid.core.datastructures.state.async.AsyncDataState.*

import java.time.LocalDateTime
import scala.util.Try


sealed trait AsyncDataState[F, S] {


  val loadingSince: Option[LocalDateTime] = None
  val value: Option[S] = None
  val failure: Option[FailureInfo[F]] = None

  val isLoading: Boolean

  def map[S2](func: S => S2): AsyncDataState[F, S2] = this.asInstanceOf[AsyncDataState[F, S2]]

  def mapIfError[F2](func: F => F2): AsyncDataState[F2, S] = this.asInstanceOf[AsyncDataState[F2, S]]

 // def mapTry[S2](func: S => Try[S2]): AsyncDataState[F, S2] = this.asInstanceOf[AsyncDataState[F, S2]]

}

object AsyncDataState {

  case class AsyncDataLoading[F, S]() extends AsyncDataState[F, S] {
    override val loadingSince: Option[LocalDateTime] = Some(LocalDateTime.now())
    val isLoading: Boolean = true
  }

  sealed trait AsyncDataStateFinished[F, S] extends AsyncDataState[F, S] {
    val asEither: Either[FailureInfo[F], S]
    val isLoading: Boolean = false

    def mapFinished[S2](func: S => S2): AsyncDataStateFinished[F, S2] = map(func).asInstanceOf[AsyncDataStateFinished[F, S2]]

  }

  case class AsyncDataSuccess[F, S](dataValue: S) extends AsyncDataStateFinished[F, S] {
    override val asEither: Either[FailureInfo[F], S] = Right(dataValue)
    override val value: Option[S] = Some(dataValue)

    override def map[S2](func: S => S2): AsyncDataStateFinished[F, S2] =
      try AsyncDataSuccess(func(dataValue))
      catch case e: Exception => {
        val err = Exception("AsyncDataSuccess::map failed because of exception: " + e.getMessage, e)
        AsyncDataFailed(SerializedException(err), None)
      }

    /*override def mapTry[S2](func: S => Try[S2]): AsyncDataState[F, S2] =
      try AsyncDataSuccess(func(dataValue).get)
      catch case e: Exception => AsyncDataFailed(SerializedException(e), None)*/

  }

  case class AsyncDataFailed[F, S](cause: SerializedException, additionalData: Option[F]) extends AsyncDataStateFinished[F, S] {
    private val failureInfo = FailureInfo(cause, additionalData)
    override val asEither: Either[FailureInfo[F], S] = Left(failureInfo)
    override val failure: Option[FailureInfo[F]] = Some(failureInfo)

    override def mapIfError[F2](func: F => F2): AsyncDataState[F2, S] =
      try AsyncDataFailed(cause, additionalData.map(func))
      catch case e: Exception => AsyncDataFailed(SerializedException(e), None)
  }

  object AsyncDataFailed {

    def apply[F, S](cause: Throwable, additionalData: Option[F] = None): AsyncDataFailed[F, S] = {
      val err = SerializedException(cause)
      AsyncDataFailed(err, additionalData)
    }

  }

}
