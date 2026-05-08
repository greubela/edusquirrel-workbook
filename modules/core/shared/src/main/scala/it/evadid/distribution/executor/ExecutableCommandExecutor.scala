package it.evadid.distribution.executor

import it.evadid.distribution.*
/*
case class ExecutableCommandExecutor(commandHandler: Set[ExecutableCommand[?]]) extends Executor {

  private lazy val commandMap: Map[String, ExecutableCommand[?]] = commandHandler.map(c => c.getClass.getName -> c).toMap

  override def canExecute(executionCommand: ExecutionCommand): Boolean = commandMap.contains(executionCommand.getClass.getName)

  override protected def handleExecution(executionCommand: ExecutionCommand, logger: Executor.Logger): ExecutionResult =
    commandMap(executionCommand.name).handleExecution(executionCommand.params)

}
*/