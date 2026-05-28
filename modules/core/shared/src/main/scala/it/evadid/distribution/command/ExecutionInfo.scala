package it.evadid.distribution.command

import it.evadid.core.util.io.TypeConverter
import it.evadid.core.util.io.serializer.DistributionSerializer
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.{TypedExecutionInfo, UntypedExecutionInfo}
import it.evadid.distribution.command.ExecutionResult.TypedExecutionResult
import upickle.default.*

import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}


trait ExecutionInfo {
  def command: ExecutionCommand

  def result: Try[ExecutionResult]

  def meta: Option[ExecutionHistory]

  def toTyped[O](converter: TypeConverter[Map[String, String], O]): TypedExecutionInfo[O] = TypedExecutionInfo[O](command, result.map(_.toTyped(converter)), meta)

  def toTyped[O](converter: Map[String, String] => O): TypedExecutionInfo[O] = TypedExecutionInfo[O](command, result.map(_.toTyped(converter)), meta)

  lazy val untyped: UntypedExecutionInfo = UntypedExecutionInfo(command, result, meta)

  def toJson: String

  def fixTime(timeRequested: LocalDateTime, timeReceived: LocalDateTime): ExecutionInfo = ExecutionInfo(command, result, Some(ExecutionHistory(timeRequested, timeReceived, meta.map(_.timestampExecutionStarted).getOrElse(timeRequested), meta.map(_.timestampExecutionFinished).getOrElse(timeRequested))))

}


object ExecutionInfo {

  def apply(command: ExecutionCommand, result: Try[ExecutionResult], meta: Option[ExecutionHistory] = None): ExecutionInfo = UntypedExecutionInfo(command, result, meta)

  def fromJson(json: String): ExecutionInfo = DistributionSerializer.serializerExecutionInfoJson.deserialize(json)

  case class UntypedExecutionInfo(command: ExecutionCommand, result: Try[ExecutionResult], meta: Option[ExecutionHistory]) extends ExecutionInfo {
    lazy val toJson: String = DistributionSerializer.serializerExecutionInfoJson.serialize(this)
  }

  case class TypedExecutionInfo[T](command: ExecutionCommand, typedResult: Try[TypedExecutionResult[T]], meta: Option[ExecutionHistory]) extends ExecutionInfo {
    def result: Try[ExecutionResult] = typedResult

    lazy val toJson: String = untyped.toJson

    def map[O](mapValue: T => O, valueToMap: O => Map[String, String]): TypedExecutionInfo[O] = TypedExecutionInfo[O](command, typedResult.map(_.map(mapValue, valueToMap)), meta)
  }

}