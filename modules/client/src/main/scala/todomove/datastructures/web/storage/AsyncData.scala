package todomove.datastructures.web.storage

import java.time.LocalDateTime

sealed trait AsyncData[T] {

  val loadingSince: Option[LocalDateTime] = None

  val value: Option[T] = None

  val failure: Option[Throwable] = None

  val asEither: Either[Throwable, Option[T]]

  def map[O](func: T => O): AsyncData[O] = {
    asEither match {
      case Left(value) => AsyncData.AsyncDataFailed(value)
      case Right(None) => AsyncData.AsyncDataLoading()
      case Right(Some(value)) => try {
        AsyncData.AsyncDataSuccess(func(value))
      } catch case e: Throwable => {
        val ex = new Exception(s"Mapping of AsyncData failed for '$value'", e)
        AsyncData.AsyncDataFailed(ex)
      }
    }
  }

  val toOption: Option[T] = asEither.match {
    case Left(value) => None
    case Right(value) => value
  }
}

object AsyncData {

  def fromOption[T](value: Option[T]): AsyncData[T] = value match {
    case None => AsyncData.AsyncDataFailed(new IllegalStateException("The option underlying AsyncData is none!"))
    case Some(v) => AsyncData.AsyncDataSuccess(v)
  }

  case class AsyncDataLoading[T]() extends AsyncData[T] {
    override val asEither: Either[Throwable, Option[T]] = Right(None)
    override val loadingSince: Option[LocalDateTime] = Some(LocalDateTime.now())
  }

  case class AsyncDataSuccess[T](dataValue: T) extends AsyncData[T] {
    override val asEither: Either[Throwable, Option[T]] = Right(Some(dataValue))
    override val value: Option[T] = Some(dataValue)
  }

  case class AsyncDataFailed[T](cause: Throwable) extends AsyncData[T] {
    override val asEither: Either[Throwable, Option[T]] = Left(cause)
    override val failure: Option[Throwable] = Some(cause)
  }

}

