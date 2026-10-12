package it.evadid.distribution.clients

import it.evadid.distribution.command.{ExecutionCommand, SerializedException}
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.WorkerExecutionProtocol.{Request, Response}
import it.evadid.util.logging.Logger
import org.scalajs.dom
import upickle.default.*
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}
import scala.util.{Failure, Success, Try}

/** A reusable ExecutionClient backed by a dedicated browser worker. */
final class WebWorkerExecutionClient private[clients](transport: WebWorkerExecutionClient.Transport,
    supportedCommands: Set[String], timeoutMilliseconds: Double) extends ExecutionClient {
  require(timeoutMilliseconds > 0 && timeoutMilliseconds.isFinite, "Worker timeout must be positive and finite")
  private case class Pending(command: ExecutionCommand, promise: Promise[ExecutionClientResponse], timer: SetTimeoutHandle)
  private val pending = mutable.Map.empty[String, Pending]
  private var nextId = 0L
  private var stopped: Option[Throwable] = None

  transport.setHandlers(receive, stop)

  override def canExecuteCommand(command: ExecutionCommand): Boolean = stopped.isEmpty && supportedCommands.contains(command.name)

  override private[clients] def handleCommandExecution(logger: Logger, command: ExecutionCommand): Future[ExecutionClientResponse] = {
    if stopped.nonEmpty then Future.failed(stopped.get)
    else if !canExecuteCommand(command) then Future.failed(SerializedException(s"Unsupported worker command: ${command.name}"))
    else {
      nextId += 1
      val id = nextId.toString
      val promise = Promise[ExecutionClientResponse]()
      val timer = setTimeout(timeoutMilliseconds) { stop(SerializedException(s"Worker request $id (${command.name}) timed out")) }
      pending(id) = Pending(command, promise, timer)
      Try(transport.postMessage(write(Request(id, command)))) match {
        case Failure(err) => stop(err)
        case _ => ()
      }
      promise.future
    }
  }

  private def receive(data: Any): Unit = {
    val decoded = Try { data match {
      case text: String => read[Response](text)
      case _ => throw SerializedException("Worker response must be a JSON string")
    }}
    decoded match {
      case Failure(err) => stop(SerializedException("Malformed worker response", err))
      case Success(reply) => pending.get(reply.requestId).foreach { request =>
        if reply.response.receivedExecutionCommand.exists(_ != request.command) ||
           (reply.response.result.isRight && !reply.response.receivedExecutionCommand.contains(request.command)) then
          stop(SerializedException("Worker response does not match the requested command"))
        else {
          pending.remove(reply.requestId)
          clearTimeout(request.timer)
          request.promise.trySuccess(reply.response)
        }
      }
    }
  }

  private def stop(cause: Throwable): Unit = if stopped.isEmpty then {
    stopped = Some(cause)
    pending.values.foreach { request => clearTimeout(request.timer); request.promise.tryFailure(cause) }
    pending.clear()
    Try(transport.terminate())
    ()
  }

  /** Also fails in-flight requests. A closed client is never silently replaced or executed on the GUI thread. */
  def close(): Unit = stop(SerializedException("Worker execution client closed"))
}

object WebWorkerExecutionClient {
  trait Transport {
    def setHandlers(onMessage: Any => Unit, onFailure: Throwable => Unit): Unit
    def postMessage(message: String): Unit
    def terminate(): Unit
  }

  def apply(scriptPath: String, timeoutMilliseconds: Double = 120000): WebWorkerExecutionClient =
    fromWorker(new dom.Worker(scriptPath), timeoutMilliseconds)

  def fromWorker(worker: dom.Worker, timeoutMilliseconds: Double = 120000): WebWorkerExecutionClient =
    new WebWorkerExecutionClient(new DomWorkerTransport(worker), WorkerCommands.supportedCommands, timeoutMilliseconds)

  private class DomWorkerTransport(worker: dom.Worker) extends Transport {
    override def setHandlers(onMessage: Any => Unit, onFailure: Throwable => Unit): Unit = {
      worker.onmessage = (event: dom.MessageEvent) => onMessage(event.data)
      worker.onerror = (event: dom.ErrorEvent) => onFailure(SerializedException(s"Worker error: ${event.message} at ${event.filename}:${event.lineno}"))
      worker.asInstanceOf[js.Dynamic].onmessageerror = ((_: dom.MessageEvent) => onFailure(SerializedException("Worker message could not be decoded"))): js.Function1[dom.MessageEvent, Unit]
    }
    override def postMessage(message: String): Unit = worker.postMessage(message)
    override def terminate(): Unit = worker.terminate()
  }
}
