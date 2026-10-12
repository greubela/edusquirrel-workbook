package it.evadid.worker

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.{ExecutionCommand, SerializedException}
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.{AdditionalLoggingOutput, ExecutionClientResponseTimestamps}
import it.evadid.distribution.formats.WorkerExecutionProtocol.{Request, Response}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import org.scalajs.dom
import upickle.default.*
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Message-based counterpart of BackendServer, using the ExecutionClient wire format. */
object WebWorkerBackendServer {
  private given ExecutionContext = ExecutionContext.global

  def fail(received: LocalDateTime, err: Throwable, command: Option[ExecutionCommand], logger: Logger): ExecutionClientResponse = {
    logger.logError(Option(err.getMessage).getOrElse(err.getClass.getSimpleName))
    ExecutionClientResponse(ExecutionClientResponseTimestamps(received, received, LocalDateTime.now()),
      Left(SerializedException(err)), command, AdditionalLoggingOutput(logger.getOut(), logger.getErr()))
  }

  def handleMessage(raw: String, executor: ExecutionClient = WorkerCommandHandler.executor): Future[String] = {
    val received = LocalDateTime.now()
    val logger = Logger.withNameAndPrefixes(Some(s"WorkerBackendServerLogger(Request@$received)"), PrintToStdLogger.printWarnAndError)
    Try(read[Request](raw)) match {
      case Failure(err) =>
        val requestId = Try(ujson.read(raw)("requestId").str).getOrElse("unknown")
        Future.successful(write(Response(requestId, fail(received, err, None, logger))))
      case Success(request) =>
        val handled = Try {
          require(request.requestId.nonEmpty, "Missing requestId")
          require(executor.canExecuteCommand(request.command), s"Unsupported worker command: ${request.command.name}")
          executor.executeResponse(logger, request.command)
        }.fold(Future.failed, identity)
        handled.map(response => response.fixReceived(received).copy(
          additionalOutput = AdditionalLoggingOutput(response.additionalOutput.loggerOut + logger.getOut(), response.additionalOutput.loggerError + logger.getErr())))
          .recover { case err => fail(received, err, Some(request.command), logger) }
          .map(response => write(Response(request.requestId, response)))
    }
  }

  def main(args: Array[String]): Unit = {
    val scope = dom.DedicatedWorkerGlobalScope.self
    scope.onmessage = (event: dom.MessageEvent) => {
      val raw = event.data match {
        case text: String => text
        case _ => "null"
      }
      handleMessage(raw).foreach(value => scope.postMessage(value))
    }
  }
}
