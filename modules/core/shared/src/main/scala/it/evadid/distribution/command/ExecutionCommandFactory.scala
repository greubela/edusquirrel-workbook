package it.evadid.distribution.command

import it.evadid.distribution.clients.{ExecutionClient, LocalExecutionClient}
import it.evadid.distribution.command.ExecutionInfo.*
import it.evadid.distribution.command.ExecutionResult.*
import it.evadid.distribution.formats.ExecutionClientResponse.AdditionalLoggingOutput
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import upickle.default
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object ExecutionCommandFactory {


}

case class ExecutionCommandFactory[I: ReadWriter, O: ReadWriter](
                                                                  name: String
                                                                ) {

  given ec: ExecutionContext = ExecutionContext.global

  private def toCommand(data: I): ExecutionCommand = ExecutionCommand(name, upickle.writeJs(data))

  def toLocalExecutionClient(handler: (I, Logger) => Future[O], mayExecuteCommand: (I, Logger) => Boolean = (i, l) => true): ExecutionClient = new LocalExecutionClient[I, O] {

    override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = executionCommand.name == name

    override def rawExecution(logger: Logger, input: I): Future[O] = {
      val mayExecute = mayExecuteCommand(input, logger)
      if (!mayExecute) Future.failed(SerializedException(s"ExecutionCommandFactory(${name}) can or may not execute on input '${input}'!")) else {
        handler.apply(input, logger)
      }
    }

  }


  def waitAndSendCommandTo(client: ExecutionClient, data: Future[I], logger: Option[Logger] = None): Future[ExecutionSuccess[O]] = {
    val timestampRequested: LocalDateTime = LocalDateTime.now()
    data.flatMap(inputData => {
      sendCommandTo(client, inputData, logger)
    })
  }

  private def createNewLogger(data: I, command: ExecutionCommand): Logger = {
    val name: String = s"DbSyncViaBackend(${command.name},${data.getClass.getSimpleName})"
    Logger.withNameAndPrefixes(Some(name), PrintToStdLogger.printWarnAndError)
  }

  def sendCommandTo(client: ExecutionClient, data: I, loggerOp: Option[Logger] = None, setTimestampRequested: Option[LocalDateTime] = None): Future[ExecutionSuccess[O]] = {
    val timestampRequestedLocal: LocalDateTime = LocalDateTime.now()
    val useTimestamp: LocalDateTime = setTimestampRequested.getOrElse(timestampRequestedLocal)

    val command: ExecutionCommand = toCommand(data)
    val logger: Logger = loggerOp.getOrElse(createNewLogger(data, command))

    val start: String = "Requested Execution of Command " + command.name + " by " + client.toString + " at " + useTimestamp + "!\n"
    val mid: String = if (setTimestampRequested.nonEmpty) "    Waited for missing data until: " + timestampRequestedLocal + "\n" else ""
    val end: String = "    Execute command with the following data: " + write(data)

    logger.logInfo(start + mid + end)

    client.handleCommand[O](logger, command).map {
      case suc: ExecutionSuccess[O] => suc
      case err: ExecutionFailure[_] => throw err.error
    }


  }

}
