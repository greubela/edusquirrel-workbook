package it.evadid.distribution

trait Executor {

  def execute(executionCommand: ExecutionCommand): ExecutionCommand.ExecutionInfo
  
}
