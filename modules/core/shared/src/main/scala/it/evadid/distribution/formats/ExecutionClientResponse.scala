package it.evadid.distribution.formats

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.*
import it.evadid.util.logging.Logger

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
    s"""
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

  private def failWith(logger: Logger, msg: String): Any = {
    logger.logWarn(msg)
    throw SerializedException(msg)
  }

  private def failWith(logger: Logger, msg: String, err: Throwable): Any = {
    logger.logExceptionWarn(msg, err)
    throw SerializedException(msg, err)
  }

  private def readFromMap[T](logger: Logger, receivedMap: Map[String, String], serializer: Serializer[T], keyStr: String): T = {
    if (!receivedMap.contains(keyStr)) {
      failWith(logger, s"ExecutionClientResponse::receivedMap does not contain ${keyStr}, keys: ${receivedMap.keys.toList.mkString(", ")}")
      throw new UnsupportedOperationException("this can not happen @ ExecutionClientResponse::readFromMap") // failWith already throws
    } else try
      serializer.deserialize(receivedMap(keyStr))
    catch case e: Throwable =>
      failWith(logger, "ignoring parsing result because of exception: " + e.getMessage, e)
      throw new UnsupportedOperationException("this can not happen @ ExecutionClientResponse::readFromMap") // failWith already throws
  }

  def parseFromDefaultMapAndUpdateLogger(logger: Logger, receivedMap: Map[String, String]): ExecutionClientResponse = {
    val loggerOut: String = try {
      readFromMap(logger, receivedMap, Serializer.stringIO, "loggerOut")
    } catch case e: Exception => {
      logger.logExceptionWarn(s"ignoring logger output received from remote source because it was not parsable: ${e.getMessage}", e)
      ""
    }
    logger.logFromExternalInfo(loggerOut)
    val loggerError: String = try {
      readFromMap(logger, receivedMap, Serializer.stringIO, "loggerError")
    } catch case e: Exception => {
      logger.logExceptionWarn(s"ignoring logger error received from remote source because it was not parsable: ${e.getMessage}", e)
      ""
    }
    logger.logFromExternalError(loggerOut)

    val timestampReceived: LocalDateTime = readFromMap(logger, receivedMap, DefaultSerializer.serializerLocalDateTimeString, "timestampReceived")
    val timestampStarted: LocalDateTime = readFromMap(logger, receivedMap, DefaultSerializer.serializerLocalDateTimeString, "timestampStarted")
    val timestampFinished: LocalDateTime = readFromMap(logger, receivedMap, DefaultSerializer.serializerLocalDateTimeString, "timestampFinished")
    val parsedCommand: Option[ExecutionCommand] =
      if (!receivedMap.contains("executionCommandReceived")) None
      else Some(readFromMap(logger, receivedMap, DefaultSerializer.serializeExecutionCommandJson, "executionCommandReceived"))

    if (receivedMap.contains("executionResultSuccess")) {
      val resMap: Map[String, String] = readFromMap(logger, receivedMap, serializerMapJson, "executionResultSuccess")
      ExecutionClientResponse(timestampReceived, timestampStarted, timestampFinished, Right(resMap), parsedCommand, loggerOut, loggerError)
    } else if (receivedMap.contains("executionResultFailed")) {
      val cause: SerializedException = readFromMap(logger, receivedMap, DefaultSerializer.serializerExceptionS, "executionResultFailed")
      ExecutionClientResponse(timestampReceived, timestampStarted, timestampFinished, Left(cause), parsedCommand, loggerOut, loggerError)
    } else {
      val msg = s"Received map does not contain neither of the following keys: executionResultSuccess, executionResultFailed (keys: ${receivedMap.keys.toList.mkString(",")}"
      logger.logError(msg)
      throw Exception(msg)
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
