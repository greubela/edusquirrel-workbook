package it.evadid.distribution.clients

import it.evadid.distribution.{ExecutionClient, ExecutionCommand}

import scala.concurrent.Future

case class ServerExecution(ip: String, port: Int) extends ExecutionClient {

  // Sends command to a server with given data and executes it there (see BackendServer for a possible backend impl)

  override def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionCommand.ExecutionInfo] = ???
  
}
