package it.evadid.homepage.webElements.editor.code.worker

import com.raquo.airstream.state.Var
import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.distribution.command.{ExecutionCommand, ExecutionCommandFactory}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

/** One worker shared by all its users on the page, created on the first request. A worker that stopped (timeout,
  * crash) refuses commands and is replaced on the next request; requests are never cancelled, so they must be
  * bounded, like static analysis and parsing.
  */
final class SharedWorker(create: () => WebWorkerExecutionClient) {
  private val current = Var(Option.empty[WebWorkerExecutionClient])

  def send[Req, Res](command: ExecutionCommandFactory[Req, Res], request: Req): Future[Res] =
    Try {
      val client = current.now().filter(_.canExecuteCommand(ExecutionCommand(command.name, ujson.Null))).getOrElse {
        val created = create()
        current.set(Some(created))
        created
      }
      command.sendCommandTo(client, request).map(_.result)
    }.fold(Future.failed, identity)
}

object SharedWorker {
  /** Static analysis and parsing for every editor and preview of the page. */
  lazy val analysis: SharedWorker = SharedWorker(() => LatestWorkerRequest.workbookWorker())
}
