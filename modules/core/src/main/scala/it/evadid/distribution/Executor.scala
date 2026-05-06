package it.evadid.distribution

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
  
}
