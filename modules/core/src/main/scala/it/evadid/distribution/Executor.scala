package it.evadid.distribution

import it.evadid.distribution.clients.SynchronizedExecution

import scala.concurrent.ExecutionContext

trait Executor {

  def canExecute(executionCommand: ExecutionCommand): Boolean

  def execute(executionCommand: ExecutionCommand): Option[ExecutionCommand.ExecutionInfo]

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
  def makeSynced(ec: ExecutionContext = ExecutionContext.global): ExecutionClient =
    SynchronizedExecution(this, ec)
  
}
