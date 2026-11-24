package it.evadid.homepage.webElements.editor.code.worker

import com.raquo.airstream.state.Var
import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.facades.Workers
import it.evadid.homepage.control.startup.ClientRuntimeConfig

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}

/** Sends one worker command per key, keeping only the latest request alive.
  *
  * Requests for the key in flight share its Future. A request for another key terminates the worker, which
  * fails the superseded Future and stops its computation (e.g. an endless loop), and starts a new worker. A
  * failed request also discards its worker, so the next request starts fresh. With `cache`, the result for the
  * latest key is answered without the worker. Results of superseded requests never reach the cache.
  */
final class LatestWorkerRequest[K, Req, Res](
    command: ExecutionCommandFactory[Req, Res],
    request: K => Req,
    createWorker: () => WebWorkerExecutionClient = () => LatestWorkerRequest.workbookWorker(),
    cache: Option[LatestResult[K, Res]] = None
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
    else cache.flatMap(_.get(key)) match {
      case Some(result) =>
        cancel()
        Future.successful(result)
      case None => pending.now().filter(_._1 == key).map(_._2).getOrElse(send(key))
    }

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
        case Success(result) =>
          pending.set(None)
          cache.foreach(_.put(key, result))
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
