package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait ExecutionClient {

  protected given ExecutionContext = ExecutionContext.global

  def canExecuteCommand(executionCommand: ExecutionCommand): Boolean

  private[clients] def handleCommandExecution(logger: Logger, executionCommand: ExecutionCommand): Future[ExecutionClientResponse]

  protected def defaultLogger: Logger = Logger.withNameAndPrefixes(Some(this.toString), PrintToStdLogger.printWarnAndError)

  def handleCommand[T: ReadWriter](logger: Logger, executionCommand: ExecutionCommand): Future[ExecutionInfo[T]] = {
    val timestampRequested: LocalDateTime = LocalDateTime.now()
    handleCommandExecution(logger, executionCommand)
      .map(_.parse[T](logger, executionCommand, timestampRequested))
  }

  def handleCommand[T: ReadWriter](executionCommand: ExecutionCommand): Future[ExecutionInfo[T]] = {
    println("[WARN] creating new logger for Execution of command: " + executionCommand.name + " with body: " + write(executionCommand.json))
    handleCommand(defaultLogger, executionCommand)
  }

  def makeSynchronized(ec: ExecutionContext): ExecutionClient = SynchronizedExecutionClient(this, ec)

  def allUnderlyingClients: List[ExecutionClient] = List(this)

  def pooledWith(other: ExecutionClient): ExecutionClient = ExecutionClientPool(allUnderlyingClients ++ other.allUnderlyingClients)

}

object ExecutionClient {


  def apply(clients: List[ExecutionClient]): ExecutionClient = ExecutionClientPool(clients)


}




