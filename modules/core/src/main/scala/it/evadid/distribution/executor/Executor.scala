package it.evadid.distribution.executor

import it.evadid.distribution.ExecutionCommand.ExecutionInfo
import it.evadid.distribution.ExecutionCommand
import it.evadid.distribution.clients.ExecutionClient

import java.util.concurrent.Executors
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

trait Executor {

  def canExecute(executionCommand: ExecutionCommand): Boolean

  def execute(executionCommand: ExecutionCommand): Try[ExecutionCommand.ExecutionInfo]

  def forceExecution(executionCommand: ExecutionCommand): ExecutionCommand.ExecutionInfo = {
    if (!canExecute(executionCommand)) {
      throw new IllegalArgumentException(s"Executor cannot execute command '${executionCommand.name}'")
    }
    execute(executionCommand).getOrElse {
      throw new IllegalStateException(s"Executor reported it can execute '${executionCommand.name}' but returned no result")
    }
  }

  /**
   * Returns a queued/synchronized execution client for this executor.
   *
   * Commands are processed in FIFO order and never in parallel.
   */
}

object Executor {
  
  
}