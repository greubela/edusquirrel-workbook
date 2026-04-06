package `export`.workers

import `export`.traits.AbstractWorkerClient
import `export`.traits.WorkerTraits.WorkerCommand

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

final class MathWorkerClient() extends AbstractWorkerClient("startMathWorkerServer", Map(), None){


  def add(a: Int, b: Int): Future[Int] =
    execute("add", a, b)

  def multiply(a: Int, b: Int): Future[Int] =
    execute("multiply", a, b)
  
  private def execute(name: String, a: Int, b: Int): Future[Int] = {
    enqueue(name, Map("a" -> a.toString, "b" -> b.toString))
      .map(_.data.get("value").flatMap(_.toIntOption).getOrElse(0))
  }
  
  
}
