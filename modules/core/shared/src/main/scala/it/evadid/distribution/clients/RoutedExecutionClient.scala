package it.evadid.distribution.clients

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.logging.Logger

import scala.concurrent.Future
import scala.util.Try

/** Selects one executor per command without falling back to another destination. */
final case class RoutedExecutionClient(route: ExecutionCommand => ExecutionClient) extends ExecutionClient {
  override def canExecuteCommand(command: ExecutionCommand): Boolean =
    Try(route(command).canExecuteCommand(command)).getOrElse(false)

  override private[clients] def handleCommandExecution(logger: Logger, command: ExecutionCommand): Future[ExecutionClientResponse] =
    Future.fromTry(Try(route(command))).flatMap(_.executeResponse(logger, command))
}
