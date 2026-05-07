package it.evadid.distribution

import it.evadid.distribution.clients.*
import it.evadid.distribution.executor.Executor

import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}
import upickle.default.*

import scala.concurrent.Future

trait ExecutionServer {

  def localExecutionClient: LocalExecutionClient

  def handleExecution(executionCommand: ExecutionCommand): Future[ExecutionInfo] = {
    localExecutionClient.executeCommand(executionCommand)
  }

}
