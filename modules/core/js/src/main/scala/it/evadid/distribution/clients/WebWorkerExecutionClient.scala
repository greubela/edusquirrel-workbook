package it.evadid.distribution.clients

import it.evadid.distribution.command.{ExecutionCommand, SerializedException}
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.facades.Workers
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.WorkerExecutionProtocol.{Request, Response}
import it.evadid.util.logging.Logger
import org.scalajs.dom
import upickle.default.*
import it.evadid.core.datastructures.state.State
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}
import scala.util.{Failure, Success, Try}

/** A reusable ExecutionClient backed by a dedicated browser worker. The client creates its transport and hands it
  * its handlers at that moment, so no message can arrive before the client is ready to receive it.
  */
final class WebWorkerExecutionClient private[clients](connect: WebWorkerExecutionClient.Connect,
    supportedCommands: Set[String], timeoutMilliseconds: Double) extends ExecutionClient {
  require(timeoutMilliseconds > 0 && timeoutMilliseconds.isFinite, "Worker timeout must be positive and finite")
  private case class Pending(command: ExecutionCommand, promise: Promise[ExecutionClientResponse], timer: SetTimeoutHandle)
  /** Request numbering, in-flight requests and the reason the client stopped, changed only as one value. */
  private case class Requests(nextId: Long = 0, pending: Map[String, Pending] = Map.empty, stopped: Option[Throwable] = None)
  private val requests = State(Requests())
  private val transport = connect(receive, stop)

  override def canExecuteCommand(command: ExecutionCommand): Boolean =
    requests.now().stopped.isEmpty && supportedCommands.contains(command.name)

  override private[clients] def handleCommandExecution(logger: Logger, command: ExecutionCommand): Future[ExecutionClientResponse] =
    requests.now().stopped match {
      case Some(cause) => Future.failed(cause)
      case None if !canExecuteCommand(command) => Future.failed(SerializedException(s"Unsupported worker command: ${command.name}"))
      case None =>
        val id = (requests.now().nextId + 1).toString
        val promise = Promise[ExecutionClientResponse]()
        val timer = setTimeout(timeoutMilliseconds) { stop(SerializedException(s"Worker request $id (${command.name}) timed out")) }
        requests.update(current => current.copy(nextId = current.nextId + 1, pending = current.pending + (id -> Pending(command, promise, timer))))
        Try(transport.postMessage(write(Request(id, command)))).failed.foreach(stop)
        promise.future
    }

  private def receive(data: Any): Unit = {
    val decoded = Try { data match {
      case text: String => read[Response](text)
      case _ => throw SerializedException("Worker response must be a JSON string")
    }}
    decoded match {
      case Failure(err) => stop(SerializedException("Malformed worker response", err))
      case Success(reply) => requests.now().pending.get(reply.requestId).foreach { request =>
        if reply.response.receivedExecutionCommand.exists(_ != request.command) ||
           (reply.response.result.isRight && !reply.response.receivedExecutionCommand.contains(request.command)) then
          stop(SerializedException("Worker response does not match the requested command"))
        else {
          requests.update(current => current.copy(pending = current.pending - reply.requestId))
          clearTimeout(request.timer)
          request.promise.trySuccess(reply.response)
        }
      }
    }
  }

  private def stop(cause: Throwable): Unit = {
    val before = requests.now()
    if before.stopped.isEmpty then {
      requests.set(before.copy(pending = Map.empty, stopped = Some(cause)))
      before.pending.values.foreach { request => clearTimeout(request.timer); request.promise.tryFailure(cause) }
      // The transport is absent only if connecting itself reported a failure.
      Option(transport).foreach(created => Try(created.terminate()))
    }
  }

  /** Also fails in-flight requests. A closed client is never silently replaced or executed on the GUI thread. */
  def close(): Unit = stop(SerializedException("Worker execution client closed"))
}

object WebWorkerExecutionClient {
  trait Transport {
    def postMessage(message: String): Unit
    def terminate(): Unit
  }

  /** Creates a transport that delivers worker messages and failures to the given handlers. */
  type Connect = (Any => Unit, Throwable => Unit) => Transport

  def apply(scriptPath: String, timeoutMilliseconds: Double = 120000): WebWorkerExecutionClient =
    fromWorker(Workers.classic(scriptPath).createLoadedInstance(), timeoutMilliseconds)

  def fromWorker(worker: dom.Worker, timeoutMilliseconds: Double = 120000): WebWorkerExecutionClient =
    new WebWorkerExecutionClient(DomWorkerTransport(worker, _, _), WorkerCommands.supportedCommands, timeoutMilliseconds)

  private class DomWorkerTransport(worker: dom.Worker, onMessage: Any => Unit, onFailure: Throwable => Unit) extends Transport {
    worker.onmessage = (event: dom.MessageEvent) => onMessage(event.data)
    worker.onerror = (event: dom.ErrorEvent) => onFailure(SerializedException(s"Worker error: ${event.message} at ${event.filename}:${event.lineno}"))
    worker.asInstanceOf[js.Dynamic].onmessageerror = ((_: dom.MessageEvent) => onFailure(SerializedException("Worker message could not be decoded"))): js.Function1[dom.MessageEvent, Unit]

    override def postMessage(message: String): Unit = worker.postMessage(message)
    override def terminate(): Unit = worker.terminate()
  }
}
