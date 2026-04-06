package `export`.traits

import `export`.traits.WorkerTraits.*
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

abstract class AbstractWorkerServer(
                                     using ec: ExecutionContext
                                   ) {

  protected val self: js.Dynamic = js.Dynamic.global.self

  protected val boundCanvas: Option[dom.html.Canvas] = None

  final def start(): Unit = {
    self.onmessage = { (e: dom.MessageEvent) =>
      val msg = e.data.asInstanceOf[js.Dynamic]
      val kind = msg.kind.asInstanceOf[String]

      kind match {
        case "request" =>
          ???
        case "bind-canvas" =>
          ???
        case other =>
          ???
      }
    }
  }

  protected def handleTask(
                            workerCommand: WorkerCommand
                          ): Future[Map[String, String]]


}