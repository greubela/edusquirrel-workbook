package it.evadid.evacuation.core.utility

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}



object ConcurrencyHelper {


  def printFuture[T](msg: String = "", future: Future[T], afterMsg: String = ": ")(implicit context: ExecutionContext): Unit = {
    future onComplete {
      case Success(value) =>
        print(msg + "success" + afterMsg + value)
      case Failure(exception) =>
        print(msg + "(failed)" + afterMsg + exception.getClass + ": " + exception.getMessage)
        exception.printStackTrace()
    }
  }




}
