package it.evadid.distribution.command

import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.distribution.clients.{ExecutionClient, LocalExecutionClient}
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoTyped
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class ExecutionCommandFactory[I, O](
                                          name: String,
                                          serializerIn: Serializer[I],
                                          serializerOut: Serializer[O],
                                          serializerForLoggingIn: I => String = (input: I) => input.toString,
                                          serializerForLoggingOut: O => String = (output: O) => output.toString
                                        ) {


  private def toCommand(data: I): ExecutionCommand = ExecutionCommand(name, TypeConverter.singleValueMap.convertToI(serializerIn.serialize(data)))

  def toLocalExecutionClient(handler: (I, Logger) => Future[O]): ExecutionClient = new LocalExecutionClient {
    def executeCommand(executionCommand: ExecutionCommand, logger: Logger): Future[Map[String, String]] = {
      val parsedInput: I = serializerIn.deserialize(TypeConverter.singleValueMap.convertToO(executionCommand.params))
      handler.apply(parsedInput, logger).map(output => TypeConverter.singleValueMap.convertToI(serializerOut.serialize(output)))(using ExecutionContext.global)
    }

    override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = executionCommand.name == name
  }

  def waitAndSendCommandTo(client: ExecutionClient, data: Future[I], logger: Option[Logger] = None): Future[ExecutionInfoTyped[O]] = {
    val timestampRequested: LocalDateTime = LocalDateTime.now()
    data.flatMap(inputData => {
      sendCommandTo(client, inputData, logger)
    })(using ExecutionContext.global)
  }

  private def createNewLogger(data: I, command: ExecutionCommand): Logger = {
    val name: String = s"DbSyncViaBackend(${command.name},${data.getClass.getSimpleName})"
    Logger.withNameAndPrefixes(Some(name), PrintToStdLogger.printEverything)
  }

  def sendCommandTo(client: ExecutionClient, data: I, loggerOp: Option[Logger] = None, setTimestampRequested: Option[LocalDateTime] = None): Future[ExecutionInfoTyped[O]] = {
    val timestampRequestedLocal: LocalDateTime = LocalDateTime.now()
    val useTimestamp: LocalDateTime = setTimestampRequested.getOrElse(timestampRequestedLocal)

    val command: ExecutionCommand = toCommand(data)
    val logger: Logger = loggerOp.getOrElse(createNewLogger(data, command))

    val start: String = "Requested Execution of Command " + command.name + " by " + client.toString + " at " + useTimestamp + "!\n"
    val mid: String = if (setTimestampRequested.nonEmpty) "    Waited for missing data until: " + timestampRequestedLocal + "\n" else ""
    val end: String = "    Execute command with the following data: " + serializerForLoggingIn(data)

    logger.logInfo(start + mid + end)

    val response: Future[ExecutionInfo] = client.handleCommand(command, logger)
    response.map(_.toTyped[O](rawMap => {
      val res: O = serializerOut.deserialize(TypeConverter.singleValueMap.convertToO(rawMap))
      logger.logInfo(s"    Successfully deserialized data received from ${client.toString}:\n    ${serializerForLoggingOut(res)}")
      res
    }))(using ExecutionContext.global)


  }

}
