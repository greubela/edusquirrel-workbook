package it.evadid.distribution

import it.evadid.distribution.ExecutionCommand.ExecutionInfo

import scala.concurrent.Future

trait ExecutionClient {

  def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionInfo]
  
}
