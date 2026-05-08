package it.evadid.distribution.command

import it.evadid.core.util.io.TypeConverter
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionInfo.TypedExecutionInfo
import it.evadid.util.Logger

import scala.concurrent.{ExecutionContext, Future}

case class ExecutionCommandFactory[I, O, R](
                                             name: String,
                                             convertIn: TypeConverter[Map[String, String], I],
                                             convertOut: TypeConverter[Map[String, String], O],
                                             finishResult: (I, O) => R
                                           ) {

  private def toCommand(data: I): ExecutionCommand = ExecutionCommand(name, convertIn.convertToI(data))

  /*
  def toAsyncExecutor(handler: (I, Logger) => Future[O]): AsyncExecutor = new AsyncExecutor() {

    def canExecute(executionCommand: ExecutionCommand): Boolean = executionCommand.name == name

    protected def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionResult] = {
      val parsedInput: I = convertIn.convertToO(executionCommand.params)
      handler.apply(parsedInput, logger)
        .map(output => {
          val rawData = convertOut.convertToI(output)
          TypedExecutionResult[O](output, logger.getOut(), logger.getErr(), rawData)
        })(using ExecutionContext.global)
    }
  }

  def toSyncExecutor(handler: (I, Logger) => O): SyncExecutor = new SyncExecutor() {
    def canExecute(executionCommand: ExecutionCommand): Boolean = executionCommand.name == name

    protected def handleExecutionSync(executionCommand: ExecutionCommand, logger: Logger): ExecutionResult = {
      val parsedInput: I = convertIn.convertToO(executionCommand.params)
      val output = handler(parsedInput, logger)
      val rawData = convertOut.convertToI(output)
      TypedExecutionResult[O](output, logger.getOut(), logger.getErr(), rawData)
    }
  }
*/
  def sendCommandTo(client: ExecutionClient, logger: Logger, data: I): Future[TypedExecutionInfo[R]] = {
    val command = toCommand(data)
    val futureOutput = client.handleExecution(command, logger)
    val futureResult = futureOutput.map(_.toTyped[R](outputMap => finishResult(data, convertOut.convertToO(outputMap))))(using ExecutionContext.global)
    futureResult
  }

}
