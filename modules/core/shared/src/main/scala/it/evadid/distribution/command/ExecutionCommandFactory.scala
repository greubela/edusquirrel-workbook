package it.evadid.distribution.command

import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.distribution.clients.{ExecutionClient, LocalExecutionClient}
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoTyped
import it.evadid.util.logging.Logger

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class ExecutionCommandFactory[I, O](
                                          name: String,
                                          serializerIn: Serializer[I],
                                          serializerOut: Serializer[O]
                                        ) {


  private def toCommand(data: I): ExecutionCommand = ExecutionCommand(name, TypeConverter.singleValueMap.convertToI(serializerIn.serialize(data)))

  def toLocalExecutionClient(handler: (I, Logger) => Future[O]): ExecutionClient = new LocalExecutionClient {
    def executeCommand(executionCommand: ExecutionCommand, logger: Logger): Future[Map[String, String]] = {
      val parsedInput: I = serializerIn.deserialize(TypeConverter.singleValueMap.convertToO(executionCommand.params))
      handler.apply(parsedInput, logger).map(output => TypeConverter.singleValueMap.convertToI(serializerOut.serialize(output)))(using ExecutionContext.global)
    }

    override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = executionCommand.name == name
  }

  def waitAndSendCommandTo(client: ExecutionClient, logger: Logger, data: Future[I]): Future[ExecutionInfoTyped[O]] = {
    data.flatMap(inputData => {
      sendCommandTo(client, logger, inputData)
    })(using ExecutionContext.global)
  }

  def sendCommandTo(client: ExecutionClient, logger: Logger, data: I): Future[ExecutionInfoTyped[O]] = {
    val timestampRequested: LocalDateTime = LocalDateTime.now()
    val command: ExecutionCommand = toCommand(data)
    val response: Future[ExecutionInfo] = client.handleCommand(command)
    response.map(_.toTyped[O](rawMap => serializerOut.deserialize(TypeConverter.singleValueMap.convertToO(rawMap))))(using ExecutionContext.global)
  }

}
