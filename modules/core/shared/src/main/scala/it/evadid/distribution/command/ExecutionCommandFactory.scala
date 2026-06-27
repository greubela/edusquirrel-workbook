package it.evadid.distribution.command

import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataStateFinished
import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.distribution.clients.{ExecutionClient, LocalExecutionClient}
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoTyped
import it.evadid.distribution.command.ExecutionResult.ExecutionResultTyped
import it.evadid.util.Logger

import scala.concurrent.{ExecutionContext, Future}

case class ExecutionCommandFactory[I, O](
                                          name: String,
                                          serializerIn: Serializer[I],
                                          serializerOut: Serializer[O]
                                        ) {


  private def toCommand(data: I): ExecutionCommand = ExecutionCommand(name, TypeConverter.singleValueMap.convertToI(serializerIn.serialize(data)))

  def toExecutionClient(handler: (I, Logger) => Future[O]): ExecutionClient = new LocalExecutionClient {
    override def calculateResult(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionResult] = {

      val parsedInput: I = serializerIn.deserialize(TypeConverter.singleValueMap.convertToO(executionCommand.params))
      handler.apply(parsedInput, logger)
        .map(output => {
          val rawData = serializerOut.serialize(output)
          val rawMap = TypeConverter.singleValueMap.convertToI(rawData)
          ExecutionResultTyped[O](output, logger.getOut(), logger.getErr(), rawMap)
        })(using ExecutionContext.global)
    }

    override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = executionCommand.name == name
  }

  def waitAndSendCommandTo(client: ExecutionClient, logger: Logger, data: Future[I]): AsyncData[Nothing, ExecutionInfoTyped[O]] = {
    val res: Future[ExecutionInfoTyped[O]] = data.flatMap(inputData => {
      val async: AsyncData[Nothing, ExecutionInfoTyped[O]] = sendCommandTo(client, logger, inputData)
      async.futureFirstValue
    })(using ExecutionContext.global)
    AsyncData.forFuture(res)
  }

  def sendCommandTo(client: ExecutionClient, logger: Logger, data: I): AsyncData[Nothing, ExecutionInfoTyped[O]] = {
    val command: ExecutionCommand = toCommand(data)
    val async: Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = client.handleExecution(command, logger)
    AsyncData.forStateFuture(async).map(_.toTyped[O](rawMap => serializerOut.deserialize(TypeConverter.singleValueMap.convertToO(rawMap))))
  }

}
