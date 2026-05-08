package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.util.Logger

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait ExecutionClient {

  def canExecuteCommand(executionCommand: ExecutionCommand): Boolean

  def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionInfo]
    
  def makeSynchronized(ec: ExecutionContext): ExecutionClient = SynchronizedExecutionClient(this, ec)

  def allUnderlyingClients: List[ExecutionClient] = List(this)
  
  def pooledWith(other: ExecutionClient): ExecutionClient = ExecutionClientPool(allUnderlyingClients ++ other.allUnderlyingClients)

}

object ExecutionClient {


  def apply(clients: List[ExecutionClient]): ExecutionClient = ExecutionClientPool(clients)

}




