package it.evadid.distribution.formats

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.*
import upickle.default.write

import java.time.LocalDateTime

case class ExecutionClientResponse(
                                    timestampReceived: LocalDateTime,
                                    timestampStarted: LocalDateTime,
                                    timestampFinished: LocalDateTime,
                                    response: Either[SerializedException, Map[String, String]],
                                    parsedExecutionCommand: Option[ExecutionCommand],
                                    loggerOut: String,
                                    loggerError: String
                                  ) {

  def serializedToMap(): Map[String, String] = {
    val baseMap = Map(
      executionInfoEntry(response),
      "timestampFinished" -> DefaultSerializer.serializerLocalDateTimeString.serialize(timestampFinished),
      "timestampStarted" -> DefaultSerializer.serializerLocalDateTimeString.serialize(timestampStarted),
      "timestampReceived" -> DefaultSerializer.serializerLocalDateTimeString.serialize(timestampReceived),
      "loggerOut" -> loggerOut,
      "loggerError" -> loggerError
    )
    val cmdMap = if (parsedExecutionCommand.isEmpty) Map() else Map(
      "executionCommandReceived" -> DefaultSerializer.serializeExecutionCommandJson.serialize(parsedExecutionCommand.get)
    )
    baseMap ++ cmdMap
  }

  def executionInfoEntry(asyncDataStateFinished: Either[SerializedException, Map[String, String]]): (String, String) = response.match {
    case Right(mapInfo) => "executionResultSuccess" -> ExecutionClientResponse.serializerMapJson.serialize(mapInfo)
    case Left(cause) => "executionResultFailed" -> DefaultSerializer.serializerExceptionS.serialize(cause)
  }

  lazy val sendFormat: (Int, String) = (200, ExecutionClientResponse.serializerMapJson.serialize(serializedToMap()))

  override lazy val toString: String =
    """
      |ExecutionClientResponse(
      |  timestampReceived=$timestampReceived,
      |  timestampStarted=$timestampStarted,
      |  timestampFinished=$timestampFinished,
      |  response=$response,
      |  loggerOut=$loggerOut,
      |  loggerError=$loggerError
      |)
      |""".stripMargin

}

object ExecutionClientResponse {

  private val serializerMapJson: Serializer[Map[String, String]] = DefaultSerializer.serializerStringMap

  def apply(receivedMap: Map[String, String]): ExecutionClientResponse = {
    val timestampReceived: LocalDateTime = DefaultSerializer.serializerLocalDateTimeString.deserialize(receivedMap("timestampReceived"))
    val timestampStarted: LocalDateTime = DefaultSerializer.serializerLocalDateTimeString.deserialize(receivedMap("timestampStarted"))
    val timestampFinished: LocalDateTime = DefaultSerializer.serializerLocalDateTimeString.deserialize(receivedMap("timestampFinished"))
    val loggerOut: String = receivedMap("loggerOut")
    val loggerError: String = receivedMap("loggerError")
    val parsedCommand: Option[ExecutionCommand] =
      if (!receivedMap.contains("executionCommandReceived")) None
      else Some(DefaultSerializer.serializeExecutionCommandJson.deserialize(receivedMap("executionCommandReceived")))

    if (receivedMap.contains("executionResultSuccess")) {
      val resMap: Map[String, String] = serializerMapJson.deserialize(receivedMap("executionResultSuccess"))
      ExecutionClientResponse(timestampReceived, timestampStarted, timestampFinished, Right(resMap), parsedCommand, loggerOut, loggerError)
    } else if (receivedMap.contains("executionResultFailed")) {
      val cause: SerializedException = DefaultSerializer.serializerExceptionS.deserialize(receivedMap("executionResultFailed"))
      ExecutionClientResponse(timestampReceived, timestampStarted, timestampFinished, Left(cause), parsedCommand, loggerOut, loggerError)
    } else {
      throw new Exception("Received map does not contain neither of the following keys: executionResultSuccess, executionResultFailed")
    }
  }


  def apply(timestampReceived: LocalDateTime, timestampStarted: LocalDateTime, customData: Map[String, String], parsedCommand: Option[ExecutionCommand], loggerOut: String, loggerErr: String): ExecutionClientResponse = {
    ExecutionClientResponse(timestampReceived, timestampStarted, LocalDateTime.now(), Right(customData), parsedCommand, loggerOut, loggerErr)
  }

  def apply(timestampReceived: LocalDateTime, timestampStarted: LocalDateTime, errorMsg: String, cause: Option[SerializedException], parsedExecutionCommand: Option[ExecutionCommand], loggerOut: String, loggerErr: String): ExecutionClientResponse = {
    val errCause: SerializedException = if (cause.nonEmpty) cause.get.asCauseOf(new Exception(errorMsg)) else SerializedException(errorMsg)
    ExecutionClientResponse(timestampReceived, timestampStarted, LocalDateTime.now(), Left(errCause), parsedExecutionCommand, loggerOut, loggerErr)
  }
}
