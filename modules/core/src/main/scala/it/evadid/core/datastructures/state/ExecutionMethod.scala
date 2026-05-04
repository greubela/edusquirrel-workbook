package it.evadid.core.datastructures.state

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


private sealed trait ExecutionMethod {
  def handleExecution[I, O](func: I => O, input: I, callback: Try[O] => Any): Unit
}

object ExecutionMethod {

  case class ExecuteLocalSync() extends ExecutionMethod {
    override def handleExecution[I, O](func: I => O, input: I, callback: Try[O] => Any): Unit =
      try {
        val output = func(input)
        callback.apply(Success(output))
      } catch {
        case e: Exception => callback.apply(Failure(e))
      }
  }

  case class ExecuteLocalAsync(ec: ExecutionContext) extends ExecutionMethod {
    override def handleExecution[I, O](func: I => O, input: I, callback: Try[O] => Any): Unit = {
      Future {
        func(input)
      }(using ec).onComplete {
        case Success(output) => callback.apply(Success(output))
        case Failure(e) => callback.apply(Failure(e))
      }(using ec)
    }
  }

  val executeSync = ExecuteLocalSync()
  val executeAsync = ExecuteLocalAsync(ExecutionContext.global)


  private case class SendToServer(url: URL)

}
