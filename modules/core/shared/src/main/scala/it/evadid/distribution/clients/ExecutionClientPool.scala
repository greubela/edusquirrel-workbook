package it.evadid.distribution.clients

import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncFuture}
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.*
import it.evadid.util.Logger

import java.time.LocalDateTime
import scala.concurrent.*

case class ExecutionClientPool(clients: List[ExecutionClient]) extends ExecutionClient {

  def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = clients.exists(_.canExecuteCommand(executionCommand))

  def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] =
    tryAllHandlersAfterEachOtherInOrder(executionCommand, logger)

  private def tryWithHandler(handler: ExecutionClient, command: ExecutionCommand, logger: Logger): Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = handler.handleExecution(command, logger)

  private def tryExecutionWithHandlers(
                                        handlers: List[ExecutionClient],
                                        command: ExecutionCommand,
                                        failures: Option[SerializedException],
                                        logger: Logger
                                      ): Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = {
    if (handlers.isEmpty && failures.isEmpty) {
      val msg = s"ExecutionClientPool: no handler for command ${command.name} registered"
      logger.logError(msg)
      val resState = AsyncDataFailed[Nothing, ExecutionInfo](SerializedException(msg), None)
      Future.successful(resState)
    } else if (handlers.isEmpty && failures.nonEmpty) {
      val errorStr: String = s"ExecutionClientPool: all handlers for command ${command.name} failed! See cause(s) for reasons.)"
      logger.logError(errorStr)
      val newException: SerializedException = failures.get.asCauseOf(Exception(errorStr))
      Future.successful(AsyncDataFailed(newException, None))
    } else {
      val async: AsyncFuture[Nothing, ExecutionInfo] = AsyncData.forStateFuture(handlers.head.handleExecution(command, logger))
      async.recoverOnErrorIgnoreError(failed => {
        val newFailure: Option[SerializedException] =
          if (failures.isEmpty) Some(failed.cause)
          else Some(SerializedException("First handler failed, trying next handler!", failures.get))
        val res: Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = tryExecutionWithHandlers(handlers.tail, command, newFailure, logger)
        res
      }).futureFirstState
    }
  }
  /*
    val headFuture: Future[AsyncDataStateFinished[Nothing, ExecutionInfoUntyped]] = handlers.head.handleExecution(command, logger)

    val promiseToFulfill: Promise[AsyncDataStateFinished[Nothing, ExecutionInfoUntyped]] = Promise[AsyncDataStateFinished[Nothing, ExecutionInfoUntyped]]()
    headFuture.onComplete {
      case Success(value) =>

    }

    val nextHandlerFut: Any = handlers.head.handleExecution(command, logger)
    //val async: AsyncData[Nothing, ExecutionInfo] = AsyncData.forFuture(nextHandlerFut.asInstanceOf[Future[AsyncDataState[Nothing, ExecutionInfo]]])
    nextHandlerFut {
      case AsyncDataSuccess(dataValue) => Future.successful(dataValue)
      case f: AsyncDataFailed[Nothing, ExecutionInfoUntyped] => {
        logger.logWarn(s"ExecutionClientPool: failed to execute command ${command.name} with handler ${handlers.head.getClass.getName}")
        tryExecutionWithHandlers(handlers.tail, command, promiseToFulfill, priorFailures ++ List(f), logger)
      }
    }
      .futureFirstValue
      .onComplete(futRes =>
        if (futRes.isSuccess) promiseToFulfill.success(futRes.get)
        else {

        })(using ExecutionContext.global)
  }*/


  private def executeWithHandlers(handlers: List[ExecutionClient], executionCommand: ExecutionCommand, logger: Logger): Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = {
    val timeExecutionRequested: LocalDateTime = LocalDateTime.now()
    tryExecutionWithHandlers(handlers, executionCommand, None, logger)
  }

  private def allThatCanExecute(executionCommand: ExecutionCommand): List[ExecutionClient] = clients.filter(_.canExecuteCommand(executionCommand))

  def tryAllHandlersAfterEachOtherInOrder(executionCommand: ExecutionCommand, logger: Logger): Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = {
    val allHandlers = allThatCanExecute(executionCommand)
    executeWithHandlers(allHandlers, executionCommand, logger)
  }

  override def allUnderlyingClients: List[ExecutionClient] = clients

}
