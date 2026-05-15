package it.evadid.executors

import it.evadid.distribution.*
import it.evadid.distribution.clients.{ExecutionClient, LocalExecutionClient}
import it.evadid.distribution.command.{ExecutionCommand, ExecutionResult}
import it.evadid.util.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class MathExecutor() extends LocalExecutionClient {

  private val canExecuteMethods: List[String] = List("add", "mult")

  override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = canExecuteMethods.contains(executionCommand.name)

  def calculateResult(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionResult] = Future {
    val res = executionCommand.name match {
      case "add" => resultFromValue[Int](executionCommand.params.values.map(_.toInt).sum)
      case "mult" => resultFromValue[Int](executionCommand.params.values.map(_.toInt).product)
      case _ => ???
    }
    res
  }(using ExecutionContext.global)


}
