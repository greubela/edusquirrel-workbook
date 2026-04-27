package `export`.workers

import `export`.traits.{AbstractWorkerServer, SynchronizedWorkerServer}
import `export`.traits.WorkerTraits.WorkerCommand
import org.scalajs.dom.OffscreenCanvas

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

final class MathWorkerServer extends AbstractWorkerServer("MathWorkerServer", true) {

  override def init(params: Map[String, String], canvas: Option[OffscreenCanvas]): Future[Boolean] = {
    val p = Promise[Boolean]()
    js.timers.setTimeout(0) {
      p.success(true)
    }
    p.future
  }

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
