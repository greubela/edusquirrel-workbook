package it.evadid.distribution.formats

import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.*
import play.api.libs.json.JsPath.read
import play.api.libs.json.{JsValue, Json}
import upickle.default.write

import java.time.LocalDateTime

case class ExecutionClientResponse(
                                    timestampReceived: LocalDateTime,
                                    timestampStarted: LocalDateTime,
                                    timestampFinished: LocalDateTime,
                                    response: Either[SerializedException, Map[String, String]],
                                    //asyncDataStateFinished: AsyncDataStateFinished[Nothing, ExecutionResult],
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
    case Right(mapInfo) => "executionResultSuccess" -> write(mapInfo)
    case Left(cause) => "executionResultFailed" -> DefaultSerializer.serializerExceptionS.serialize(cause)
  }

  lazy val sendFormat: (Int, String) = (200, write(serializedToMap()))

  lazy val toString: String =
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
      val either: Either[SerializedException, Map[String, String]] = readJson(receivedMap.get("executionResultSuccess"))
      ExecutionClientResponse(timestampReceived, timestampStarted, timestampFinished, either, parsedCommand, loggerOut, loggerError)
    } else if (receivedMap.contains("executionResultFailed")) {
      val cause: SerializedException = DefaultSerializer.serializerExceptionS.deserialize(receivedMap("executionInfoError"))
      ExecutionClientResponse(timestampReceived, timestampStarted, timestampFinished, Left(cause), parsedCommand, loggerOut, loggerError)
    } else {
      throw new Exception("Received map does not contain neither of the following keys: executionResultSuccess, executionResultFailed")
    }
  }

  private def readJson(json: Option[String]): Either[SerializedException, Map[String, String]] = json.match {
    case None => Left(SerializedException("No JSON provided!"))
    case Some(jsonStr) => {
      val jsonValue: JsValue = Json.parse(jsonStr)
      val safeMap: Option[Map[String, String]] = jsonValue.asOpt[Map[String, String]]
      safeMap match {
        case Some(map) => Right(map)
        case None => Left(SerializedException("Could not parse JSON str: " + jsonStr))
      }
    }
  }

  def apply(timestampReceived: LocalDateTime, timestampStarted: LocalDateTime, customData: Map[String, String], parsedCommand: Option[ExecutionCommand]): ExecutionClientResponse = {
    ExecutionClientResponse(timestampReceived, timestampStarted, LocalDateTime.now(), Right(customData), parsedCommand)
  }

  def apply(timestampReceived: LocalDateTime, timestampStarted: LocalDateTime, errorMsg: String, cause: Option[SerializedException], parsedExecutionCommand: Option[ExecutionCommand]): ExecutionClientResponse = {
    val errCause: SerializedException = if (cause.nonEmpty) cause.get.asCauseOf(new Exception(errorMsg)) else SerializedException(errorMsg)
    ExecutionClientResponse(timestampReceived, timestampStarted, LocalDateTime.now(), Left(errCause), parsedExecutionCommand)
  }
}
