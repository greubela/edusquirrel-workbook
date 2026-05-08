package it.evadid.distribution.clients

import it.evadid.distribution.clients.*
import it.evadid.util.Logger
import it.evadid.distribution.command.*
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}

case class ExecutionClientPool(clients: List[ExecutionClient]) extends ExecutionClient {

  def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = clients.exists(_.canExecuteCommand(executionCommand))

  def handleExecution(executionCommand: ExecutionCommand, logger: Logger = Logger()): Future[ExecutionInfo] = tryAllHandlersAfterEachOtherInOrder(executionCommand, logger)

  private def tryWithHandler(handler: ExecutionClient, command: ExecutionCommand, logger: Logger): Future[ExecutionInfo] = handler.handleExecution(command, logger)

  private def tryExecutionWithHandlers(handlers: List[ExecutionClient], command: ExecutionCommand, promiseToFulfill: Promise[ExecutionInfo], priorFailedExecutions: Int, logger: Logger): Unit = {
    if (handlers.isEmpty) {
      logger.logError(s"ExecutionClientPool: no handler for command ${command.name} available ($priorFailedExecutions attempts)")
      promiseToFulfill.failure(new IllegalStateException(s"no handler for command ${command.name} available ($priorFailedExecutions attempts)"))
    } else {
      handlers.head.handleExecution(command, logger)
        .onComplete(futRes =>
          if (futRes.isSuccess) promiseToFulfill.success(futRes.get)
          else {
            logger.logError(s"ExecutionClientPool: failed to execute command ${command.name} with handler ${handlers.head.getClass.getName} ($priorFailedExecutions attempts)")
            tryExecutionWithHandlers(handlers.tail, command, promiseToFulfill, priorFailedExecutions + 1, logger)
          })(using ExecutionContext.global)
    }
  }

  private def executeWithHandlers(handlers: List[ExecutionClient], executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionInfo] = {
    val timeExecutionRequested: LocalDateTime = LocalDateTime.now()
    val resultPromise = Promise[ExecutionInfo]()
    tryExecutionWithHandlers(handlers, executionCommand, resultPromise, 0, logger)
    resultPromise.future.map(info => info.fixTime(timeExecutionRequested, info.meta.map(_.timestampCommandReceived).getOrElse(timeExecutionRequested)))(using ExecutionContext.global)
  }

  private def allThatCanExecute(executionCommand: ExecutionCommand): List[ExecutionClient] = clients.filter(_.canExecuteCommand(executionCommand))

  def tryAllHandlersAfterEachOtherInOrder(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionInfo] = {
    val allHandlers = allThatCanExecute(executionCommand)
    executeWithHandlers(allHandlers, executionCommand, logger)
  }

  override def allUnderlyingClients: List[ExecutionClient] = clients

}
