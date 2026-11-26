package it.evadid.homepage.webElements.editor.code.worker

import com.raquo.airstream.state.Var
import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.facades.Workers
import it.evadid.homepage.control.startup.ClientRuntimeConfig

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}

/** Sends one worker command per key on a worker of its own, keeping only the latest request alive: for work that
  * may not end, such as running a program.
  *
  * Requests for the key in flight share its Future. A request for another key terminates the worker, which
  * fails the superseded Future and stops its computation (e.g. an endless loop), and starts a new worker. A
  * failed request also discards its worker, so the next request starts fresh. Bounded work shared by the page
  * uses [[SharedWorker]] instead.
  */
final class LatestWorkerRequest[K, Req, Res](
    command: ExecutionCommandFactory[Req, Res],
    request: K => Req,
    createWorker: () => WebWorkerExecutionClient = () => LatestWorkerRequest.workbookWorker()
) {
  private val worker = Var(Option.empty[WebWorkerExecutionClient])
  private val pending = Var(Option.empty[(K, Future[Res])])
  private val generation = Var(0)
  private val closed = Var(false)

  private def discardWorker(): Unit = {
    generation.update(_ + 1)
    worker.now().foreach(_.close())
    worker.set(None)
    pending.set(None)
  }

  /** Terminates the worker; the instance then rejects requests. */
  def close(): Unit = {
    closed.set(true)
    discardWorker()
  }

  /** Stops a request in flight; later requests start a new worker. */
  def cancel(): Unit = if pending.now().nonEmpty then discardWorker()

  def apply(key: K): Future[Res] =
    if closed.now() then Future.failed(IllegalStateException("The worker request is closed"))
    else pending.now().filter(_._1 == key).map(_._2).getOrElse(send(key))

  private def send(key: K): Future[Res] = {
    cancel()
    val current = generation.now()
    val sent = Try {
      val executor = worker.now().getOrElse {
        val created = createWorker()
        worker.set(Some(created))
        created
      }
      command.sendCommandTo(executor, request(key)).map(_.result)
    }.fold(Future.failed, identity)
    val completed = sent.transform { outcome =>
      if !closed.now() && current == generation.now() then outcome match {
        case Success(_) => pending.set(None)
        case Failure(_) => discardWorker()
      }
      outcome
    }
    pending.set(Some(key -> completed))
    completed
  }
}

object LatestWorkerRequest {
  /** A new instance of the workbook worker; `timeoutMilliseconds` bounds each request. */
  def workbookWorker(timeoutMilliseconds: Double = 30000): WebWorkerExecutionClient =
    WebWorkerExecutionClient.fromWorker(Workers.classic(ClientRuntimeConfig.workerUrl).createLoadedInstance(), timeoutMilliseconds)
}
