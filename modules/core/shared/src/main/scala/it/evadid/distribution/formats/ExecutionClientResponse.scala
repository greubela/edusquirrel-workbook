package it.evadid.distribution.formats

import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.{ExecutionFailure, ExecutionSuccess}
import it.evadid.distribution.formats.ExecutionClientResponse.{AdditionalLoggingOutput, ExecutionClientResponseTimestamps, SerializedValue}
import it.evadid.util.logging.Logger
import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import java.time.LocalDateTime

object ExecutionClientResponse {

  case class ExecutionClientResponseTimestamps(timestampReceived: LocalDateTime, timestampStarted: LocalDateTime, timestampFinished: LocalDateTime) derives ReadWriter {
    def toHistory(timestampRequested: LocalDateTime): ExecutionHistory = {
      ExecutionHistory(timestampRequested, timestampReceived, timestampStarted, timestampFinished)
    }
  }

  case class AdditionalLoggingOutput(loggerOut: String, loggerError: String) derives ReadWriter

  object AdditionalLoggingOutput {
    val empty = AdditionalLoggingOutput("", "")
  }

  case class SerializedValue(json: String, classSimpleName: String) derives ReadWriter{
    def getAs[T](using rw: ReadWriter[T]): T = read(json)(using rw)
  }

  object SerializedValue {
    def apply[T: ReadWriter](obj: T) = new SerializedValue(json = write(obj), classSimpleName = obj.getClass.getSimpleName)
  }


  def forValue[T: ReadWriter](timestamps: ExecutionClientResponseTimestamps, result: T, cmd: ExecutionCommand, output: AdditionalLoggingOutput = AdditionalLoggingOutput.empty): ExecutionClientResponse = {
    try{
      ExecutionClientResponse(timestamps, Right(SerializedValue(result)), Some(cmd), output)
    }catch case (err: Throwable) => {
      ExecutionClientResponse.forError(timestamps, SerializedException(s"Error while trying to build ExecutionClientResponse with ${result}", err), cmd, output)
    }
  }

  def forError(timestamps: ExecutionClientResponseTimestamps, err: SerializedException, cmd: ExecutionCommand, output: AdditionalLoggingOutput = AdditionalLoggingOutput.empty): ExecutionClientResponse = {
    ExecutionClientResponse(timestamps, Left(err), Some(cmd), output)
  }

}

case class ExecutionClientResponse(
                                    timestamps: ExecutionClientResponseTimestamps,
                                    result: Either[SerializedException, SerializedValue],
                                    receivedExecutionCommand: Option[ExecutionCommand],
                                    additionalOutput: AdditionalLoggingOutput
                                  ) derives ReadWriter {

  def fixReceived(newReceived: LocalDateTime): ExecutionClientResponse = this.copy(timestamps = timestamps.copy(timestampReceived = newReceived))

  def throwIfContainsError: ExecutionClientResponse = if (result.isLeft) throw result.left.get else this

  def successOrThrow[T: ReadWriter](logger: Logger, commandSent: ExecutionCommand, timestampRequested: LocalDateTime): ExecutionSuccess[T] = parse(logger, commandSent, timestampRequested).match {
    case s: ExecutionSuccess[T] => s
    case f: ExecutionFailure[?] => throw f.error
  }

  def parse[T: ReadWriter](logger: Logger, commandSent: ExecutionCommand, timestampRequested: LocalDateTime): ExecutionInfo[T] = {
    val history = timestamps.toHistory(timestampRequested)
    result.match {
      case Right(exRes) => try {
        ExecutionSuccess(commandSent, history, exRes.getAs[T])
      } catch case (err: Throwable) => {
        val wasName = result.getClass.getSimpleName
        ExecutionFailure(commandSent, Some(history), SerializedException(s"ExecutionClientResponse had an result but of the wrong type (was $wasName"))
      }
      case Left(err) => ExecutionFailure(commandSent, Some(history), err)
    }
  }


}

