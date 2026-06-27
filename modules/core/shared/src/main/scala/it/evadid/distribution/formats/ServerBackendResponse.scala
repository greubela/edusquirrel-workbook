package it.evadid.distribution.formats

import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.*
import upickle.default.write

import java.time.LocalDateTime
import scala.util.Try


case class BackendServerResponse(
                                  timestampReceived: LocalDateTime,
                                  timestampFinished: LocalDateTime,
                                  asyncDataStateFinished: AsyncDataStateFinished[Nothing, ExecutionInfo],
                                  parsedExecutionCommand: Option[ExecutionCommand]
                                ) {

  def serializedToMap(): Map[String, String] = {
    val baseMap = Map(
      executionInfoEntry(asyncDataStateFinished),
      "timestampFinished" -> DefaultSerializer.serializerLocalDateTimeString.serialize(timestampFinished),
      "timestampReceived" -> DefaultSerializer.serializerLocalDateTimeString.serialize(timestampReceived)
    )
    val cmdMap = if (parsedExecutionCommand.isEmpty) Map() else Map(
      "executionCommandReceived" -> DefaultSerializer.serializeExecutionCommandJson.serialize(parsedExecutionCommand.get)
    )
    baseMap ++ cmdMap
  }

  def executionInfoEntry(asyncDataStateFinished: AsyncDataStateFinished[Nothing, ExecutionInfo]): (String, String) = asyncDataStateFinished.match {
    case AsyncDataSuccess(exInfo) => "executionInfoSuccess" -> DefaultSerializer.serializerExecutionInfoJson.serialize(exInfo.toUntyped)
    case AsyncDataFailed(cause, additionalData) => "executionInfoError" -> DefaultSerializer.serializerExceptionS.serialize(cause)
  }

  lazy val sendFormat: (Int, String) = (200, write(serializedToMap()))

}

object BackendServerResponse {

  def apply(receivedMap: Map[String, String]): Try[BackendServerResponse] = Try {
    val timestampReceived: LocalDateTime = DefaultSerializer.serializerLocalDateTimeString.deserialize(receivedMap("timestampReceived"))
    val timestampFinished: LocalDateTime = DefaultSerializer.serializerLocalDateTimeString.deserialize(receivedMap("timestampFinished"))
    val parsedCommand: Option[ExecutionCommand] =
      if (!receivedMap.contains("executionCommandReceived")) None
      else Some(DefaultSerializer.serializeExecutionCommandJson.deserialize(receivedMap("executionCommandReceived")))

    if (receivedMap.contains("executionInfoSuccess")) {
      val executionInfo: ExecutionInfo = DefaultSerializer.serializerExecutionInfoJson.deserialize(receivedMap("executionInfoSuccess"))
      BackendServerResponse(timestampReceived, timestampFinished, AsyncDataSuccess(executionInfo), parsedCommand)
    } else if (receivedMap.contains("executionInfoError")) {
      val cause: SerializedException = DefaultSerializer.serializerExceptionS.deserialize(receivedMap("executionInfoError"))
      BackendServerResponse(timestampReceived, timestampFinished, AsyncDataFailed(cause, None), parsedCommand)
    } else {
      throw new Exception("Received map does not contain neither of the following keys: executionInfoSuccess, executionInfoError")
    }
  }

  def apply(timestampReceived: LocalDateTime, dataFinished: AsyncDataStateFinished[Nothing, ExecutionInfo], parsedCommand: Option[ExecutionCommand]): BackendServerResponse = {
    BackendServerResponse(timestampReceived, LocalDateTime.now(), dataFinished, parsedCommand)
  }

  def apply(timestampReceived: LocalDateTime, errorMsg: String, cause: Option[SerializedException], parsedExecutionCommand: Option[ExecutionCommand]): BackendServerResponse = {
    val errCause: SerializedException = if (cause.nonEmpty) cause.get.asCauseOf(new Exception(errorMsg)) else SerializedException(errorMsg)
    apply(timestampReceived, AsyncDataFailed(errCause, None), parsedExecutionCommand)
  }
}
