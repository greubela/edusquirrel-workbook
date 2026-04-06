package `export`.workers

import `export`.traits.AbstractWorkerServer
import `export`.traits.WorkerTraits.WorkerCommand

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.annotation.JSExportTopLevel

final class MathWorkerServer extends AbstractWorkerServer {

  override def init(): Future[Boolean] = Future.successful(true)

  override protected def handleTask(workerCommand: WorkerCommand): Future[Map[String, String]] = {
    val a = workerCommand.params.get("a").flatMap(_.toIntOption).getOrElse(0)
    val b = workerCommand.params.get("b").flatMap(_.toIntOption).getOrElse(0)

    workerCommand.name match {
      case "add" => Future.successful(Map("value" -> (a + b).toString))
      case "multiply" => Future.successful(Map("value" -> (a * b).toString))
      case other => Future.failed(new IllegalArgumentException(s"Unknown math command '$other'"))
    }
  }
}

object MathWorkerServer {

  @JSExportTopLevel("startMathWorkerServer")
  def startMathWorkerServer(): Unit =
    new MathWorkerServer().start()
}
