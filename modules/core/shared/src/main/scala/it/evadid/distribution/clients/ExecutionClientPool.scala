package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.logging.Logger
import java.time.LocalDateTime
import scala.concurrent.Future
import scala.util.control.NonFatal

case class ExecutionClientPool(clients: List[ExecutionClient]) extends ExecutionClient {
  def canExecuteCommand(command: ExecutionCommand): Boolean = clients.exists(_.canExecuteCommand(command))

  override def allUnderlyingClients: List[ExecutionClient] = clients

  override private[clients] def handleCommandExecution(logger: Logger, command: ExecutionCommand): Future[ExecutionClientResponse] = {
    val received = LocalDateTime.now()

    def attempt(handlers: List[ExecutionClient], failure: Option[SerializedException]): Future[ExecutionClientResponse] = handlers match {
      case Nil =>
        val message = failure.fold(s"ExecutionClientPool: no handler for command ${command.name} registered!") { error =>
          s"ExecutionClientPool: all handlers for command ${command.name} failed! (reason: ${error.msg})"
        }
        logger.logError(message)
        Future.failed(SerializedException(message).copy(cause = failure))
      case handler :: remaining =>
        logger.logInfo(s"Tried execution with handler $handler at ${LocalDateTime.now()} (prior failures: ${failure.map(_.msg).getOrElse("none")})")
        // Capture handlers that throw before returning their Future as well as asynchronous failures.
        Future.unit.flatMap(_ => handler.handleCommandExecution(logger, command))
          .map(_.fixReceived(received).throwIfContainsError)
          .recoverWith { case NonFatal(error) =>
            logger.logError(s"Execution Client $handler failed execution at ${LocalDateTime.now()}, trying next handler!\n${error.getMessage}")
            val current = error match {
              case serialized: SerializedException => serialized
              case other => SerializedException(other)
            }
            val combined = failure.fold(current)(previous =>
              SerializedException(current.msg + "; previous handler: " + previous.msg).copy(cause = Some(current)))
            attempt(remaining, Some(combined))
          }
    }

    attempt(clients.filter(_.canExecuteCommand(command)), None)
  }
}
