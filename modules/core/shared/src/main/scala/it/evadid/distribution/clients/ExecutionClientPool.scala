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

  def buildExceptionStack(lastMsg: String, exceptions: List[Throwable]): Exception = {
    if (exceptions.isEmpty) {
      new Exception(lastMsg)
    }
    else {
      val priorException = buildExceptionStack(exceptions.head.getMessage, exceptions.tail)
      new Exception(lastMsg, priorException)
    }
  }

  private def tryExecutionWithHandlers(handlers: List[ExecutionClient], command: ExecutionCommand, promiseToFulfill: Promise[ExecutionInfo], priorFailures: List[Throwable], logger: Logger): Unit = {
    if (handlers.isEmpty) {
      val errorStr: String =
        if (priorFailures.isEmpty) s"ExecutionClientPool: no handler for command ${command.name} registered"
        else s"ExecutionClientPool: all handlers for command ${command.name} failed (${priorFailures.size} attempts: ${priorFailures.size})"
      logger.logError(errorStr)
      if (priorFailures.nonEmpty) {
        logger.logError(priorFailures.map(_.getMessage).mkString("\n    ", "\n    ", "\n"))
      }
      promiseToFulfill.failure(buildExceptionStack(errorStr, priorFailures))
    } else {
      handlers.head.handleExecution(command, logger)
        .onComplete(futRes =>
          if (futRes.isSuccess) promiseToFulfill.success(futRes.get)
          else {
            logger.logError(s"ExecutionClientPool: failed to execute command ${command.name} with handler ${handlers.head.getClass.getName}")
            tryExecutionWithHandlers(handlers.tail, command, promiseToFulfill, priorFailures ++ List(futRes.failed.get), logger)
          })(using ExecutionContext.global)
    }
  }

  private def executeWithHandlers(handlers: List[ExecutionClient], executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionInfo] = {
    val timeExecutionRequested: LocalDateTime = LocalDateTime.now()
    val resultPromise = Promise[ExecutionInfo]()
    tryExecutionWithHandlers(handlers, executionCommand, resultPromise, List(), logger)
    resultPromise.future.map(info => info.fixTime(timeExecutionRequested, info.meta.map(_.timestampCommandReceived).getOrElse(timeExecutionRequested)))(using ExecutionContext.global)
  }

  private def allThatCanExecute(executionCommand: ExecutionCommand): List[ExecutionClient] = clients.filter(_.canExecuteCommand(executionCommand))

  def tryAllHandlersAfterEachOtherInOrder(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionInfo] = {
    val allHandlers = allThatCanExecute(executionCommand)
    executeWithHandlers(allHandlers, executionCommand, logger)
  }

  override def allUnderlyingClients: List[ExecutionClient] = clients

}
