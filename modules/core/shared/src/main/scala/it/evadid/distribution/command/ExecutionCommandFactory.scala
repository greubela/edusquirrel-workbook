package it.evadid.distribution.command

import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.distribution.clients.{ExecutionClient, LocalExecutionClient}
import it.evadid.distribution.command.ExecutionInfo.TypedExecutionInfo
import it.evadid.distribution.command.ExecutionResult.TypedExecutionResult
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
          TypedExecutionResult[O](output, logger.getOut(), logger.getErr(), rawMap)
        })(using ExecutionContext.global)
    }

    override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = executionCommand.name == name
  }

  def waitAndSendCommandTo(client: ExecutionClient, logger: Logger, data: Future[I]): Future[TypedExecutionInfo[O]] = {
    val res = data.flatMap(inputData => sendCommandTo(client, logger, inputData))(using ExecutionContext.global)
    res
  }

  def sendCommandTo(client: ExecutionClient, logger: Logger, data: I): Future[TypedExecutionInfo[O]] = {
    val command = toCommand(data)

    client.handleExecution(command, logger)
      .map(_.toTyped[O](rawMap => serializerOut.deserialize(TypeConverter.singleValueMap.convertToO(rawMap))))(using ExecutionContext.global)
  }

}
