package it.evadid.distribution.executor

import it.evadid.core.util.io.TypeConverter
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.{ExecutionCommand, ExecutionInfo, ExecutionResult}

import scala.concurrent.Future

trait ExecutableCommand[T] {

  def name: String

  def dataConverter: TypeConverter[Map[String, String], T]

  def handleExecution(data: T): ExecutionResult

  def toCommand(data: T): ExecutionCommand = ExecutionCommand(name, dataConverter.convertToI(data))

  def requestExecution(client: ExecutionClient, data: T): Future[ExecutionInfo] = client.executeCommand(toCommand(data))

  def handleExecution(data: Map[String, String]): ExecutionResult = handleExecution(dataConverter.convertToO(data))

}

object ExecutableCommand {

  case class BasicExecutableCommand[T](name: String, dataConverter: TypeConverter[Map[String, String], T], executionHandler: T => ExecutionResult) extends ExecutableCommand[T] {
    override def handleExecution(data: T): ExecutionResult = executionHandler.apply(data)
  }

  def apply[T](name: String, dataConverter: TypeConverter[Map[String, String], T], executionHandler: T => ExecutionResult) = BasicExecutableCommand(name, dataConverter, executionHandler)

}