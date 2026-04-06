package `export`.workers

import `export`.traits.AbstractWorkerClient
import `export`.traits.WorkerTraits.WorkerCommand

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

final class MathWorkerClient(
                              exportedName: String = "startMathWorkerServer",
                              autoInit: Boolean = true
                            ) {

  private val workerClient = AbstractWorkerClient(exportedName, autoInit)

  def add(a: Int, b: Int): Future[Int] =
    execute("add", a, b)

  def multiply(a: Int, b: Int): Future[Int] =
    execute("multiply", a, b)

  def init(): Future[Boolean] = workerClient.init()

  def terminate(): Unit = workerClient.terminate()

  private def execute(name: String, a: Int, b: Int): Future[Int] = {
    workerClient
      .enqueue(
        WorkerCommand(
          name = name,
          params = Map("a" -> a.toString, "b" -> b.toString),
          timestampRequested = LocalDateTime.now()
        )
      )
      .map(_.data.get("value").flatMap(_.toIntOption).getOrElse(0))
  }
}
