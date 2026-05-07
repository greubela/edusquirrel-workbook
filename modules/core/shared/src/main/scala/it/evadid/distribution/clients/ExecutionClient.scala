package it.evadid.distribution.clients

import it.evadid.distribution.ExecutionCommand
import it.evadid.distribution.*

import scala.concurrent.Future

trait ExecutionClient {

  def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionInfo]

}



