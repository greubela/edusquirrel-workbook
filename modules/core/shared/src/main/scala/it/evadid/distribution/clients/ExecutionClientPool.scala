package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.Logger

import java.time.LocalDateTime
import scala.concurrent.*

case class ExecutionClientPool(clients: List[ExecutionClient]) extends ExecutionClient {

  def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = clients.exists(_.canExecuteCommand(executionCommand))

  private def failFuture(logger: Logger, msg: String, cause: Option[Throwable] = None): Future[ExecutionClientResponse] = {
    val err = if (cause.nonEmpty) SerializedException(msg + " (reason: " + cause.get.getMessage + ")", cause) else SerializedException(msg)
    logger.logError(err.getMessage)
    if(cause.nonEmpty) logger.logError(cause.get.getMessage + "\n" + cause.get.getStackTrace.mkString("\n"))
    throw err
  }

  private def allThatCanExecute(executionCommand: ExecutionCommand): List[ExecutionClient] = clients.filter(_.canExecuteCommand(executionCommand))

  override def allUnderlyingClients: List[ExecutionClient] = clients

  override def handleExecution(executionCommand: ExecutionCommand): Future[ExecutionClientResponse] = {
    val timestampReceived: LocalDateTime = LocalDateTime.now()
    val allHandlers = allThatCanExecute(executionCommand)
    tryExecutionWithHandlers(timestampReceived, allHandlers, executionCommand, None, Logger())
      .map(response => response.copy(timestampReceived = timestampReceived))(using ExecutionContext.global)
  }

  private def tryExecutionWithHandlers(
                                        timestampReceived: LocalDateTime,
                                        handlers: List[ExecutionClient],
                                        command: ExecutionCommand,
                                        failures: Option[SerializedException],
                                        logger: Logger
                                      ): Future[ExecutionClientResponse] = {
    if (handlers.isEmpty && failures.isEmpty) {
      failFuture(logger, s"ExecutionClientPool: no handler for command ${command.name} registered!", failures)
    } else if (handlers.isEmpty && failures.nonEmpty) {
      val msg = s"ExecutionClientPool: all handlers for command ${command.name} failed! See logs or cause(s) for reasons."
      val newFailure: SerializedException = failures.get.asCauseOf(new Exception(msg, failures.get))
      failFuture(logger, s"ExecutionClientPool: all handlers for command ${command.name} failed! See logs or cause(s) for reasons.)", Some(newFailure))
    } else {
      val timestampStarted: LocalDateTime = LocalDateTime.now()
      logger.logInfo("Tried execution with handler " + handlers.head + " at " + timestampStarted + " (prior failures: " + failures.map(_.getMessage).getOrElse("none") + ")")
      handlers.head.handleExecutionRaw(command, logger)
        .map(resMap => {
          val timestampFinished: LocalDateTime = LocalDateTime.now()
          logger.logInfo("ExecutionClientPool: execution succeeded with handler " + handlers.head + " at " + timestampFinished)
          ExecutionClientResponse(timestampReceived, timestampStarted, timestampFinished, Right(resMap), Some(command), logger.getOut(), logger.getErr())
        })(using ExecutionContext.global)
        .recoverWith((err: Throwable) =>
          val msg = s"Execution Client ${handlers.head} failed execution at ${LocalDateTime.now()}, trying next handler!"
          logger.logError(msg + "\n" + err.getMessage + "\n" + err.getStackTrace.mkString("\n"))
          val newFailure: SerializedException = if (failures.isEmpty) SerializedException(err) else failures.get.asCauseOf(Exception(msg))
          tryExecutionWithHandlers(timestampReceived, handlers.tail, command, Some(newFailure), logger)
        )(using ExecutionContext.global)
    }
  }


}
