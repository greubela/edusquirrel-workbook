package it.evadid.executors

import it.evadid.distribution.*
import it.evadid.distribution.executor.Executor
import it.evadid.distribution.executor.Executor.Logger

import scala.util.Try

case class MathExecutor() extends Executor{
  
  private val canExecuteMethods: List[String] = List("add", "mult")
  
  override def canExecute(executionCommand: ExecutionCommand): Boolean = canExecuteMethods.contains(executionCommand.name)

  protected def handleExecution(executionCommand: ExecutionCommand, logger: Logger): ExecutionResult = {
    executionCommand.name match {
      case "add" => resultFromValue[Int](executionCommand.params.values.map(_.toInt).sum)
      case "mult" => resultFromValue[Int](executionCommand.params.values.map(_.toInt).product)
      case _ => ???
    }
  }

  
  
}
