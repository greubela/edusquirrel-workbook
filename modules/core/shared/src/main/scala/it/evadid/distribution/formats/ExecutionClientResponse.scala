package it.evadid.distribution.formats

import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.{ExecutionFailure, ExecutionSuccess}
import it.evadid.distribution.formats.ExecutionClientResponse.{AdditionalLoggingOutput, ExecutionClientResponseTimestamps, SerializedValue}
import it.evadid.util.logging.Logger
import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import java.time.Instant
import scala.util.Try

object ExecutionClientResponse {

  case class ExecutionClientResponseTimestamps(timestampReceived: Instant, timestampStarted: Instant, timestampFinished: Instant) derives ReadWriter {
    def toHistory(timestampRequested: Instant): ExecutionHistory = {
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
    Try(ExecutionClientResponse(timestamps, Right(SerializedValue(result)), Some(cmd), output)).fold(
      error => forError(timestamps, SerializedException("Could not serialize execution result", error), cmd, output),
      identity)
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

  def fixReceived(newReceived: Instant): ExecutionClientResponse = this.copy(timestamps = timestamps.copy(timestampReceived = newReceived))

  def throwIfContainsError: ExecutionClientResponse = result.fold(throw _, _ => this)

  def successOrThrow[T: ReadWriter](logger: Logger, commandSent: ExecutionCommand, timestampRequested: Instant): ExecutionSuccess[T] = parse(logger, commandSent, timestampRequested).match {
    case s: ExecutionSuccess[T] => s
    case f: ExecutionFailure[?] => throw f.error
  }

  def parse[T: ReadWriter](logger: Logger, commandSent: ExecutionCommand, timestampRequested: Instant): ExecutionInfo[T] = {
    val history = timestamps.toHistory(timestampRequested)
    result.match {
      case Right(value) => Try(value.getAs[T]).fold(
        error => ExecutionFailure(commandSent, Some(history), SerializedException(
          s"ExecutionClientResponse had a result of the wrong type (was ${value.classSimpleName})", error)),
        decoded => ExecutionSuccess(commandSent, history, decoded))
      case Left(err) => ExecutionFailure(commandSent, Some(history), err)
    }
  }


}

