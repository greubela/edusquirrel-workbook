package it.evadid.distribution.command

import it.evadid.core.util.io.TypeConverter
import it.evadid.core.util.io.serializer.DistributionSerializer
import it.evadid.distribution.*
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionResult.*
import upickle.default.*

import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}

trait ExecutionResult {

  val data: Map[String, String]
  val stdOut: String
  val stdErr: String

  def toTyped[O](converter: TypeConverter[Map[String, String], O]): TypedExecutionResult[O] = TypedExecutionResult(converter.convertToO(data), stdOut, stdErr, data)

  def toTyped[O](converter: Map[String, String] => O): TypedExecutionResult[O] = TypedExecutionResult(converter.apply(data), stdOut, stdErr, data)

  lazy val untyped: UntypedExecutionResult = UntypedExecutionResult(data, stdOut, stdErr)

  lazy val toJson: String

}

object ExecutionResult {

  def fromJson(string: String): ExecutionResult = DistributionSerializer.serializerExecutionResultJson.deserialize(string)

  def apply(data: Map[String, String], stdOut: String, stdErr: String): ExecutionResult = UntypedExecutionResult(data, stdOut, stdErr)

  case class UntypedExecutionResult(data: Map[String, String], stdOut: String, stdErr: String) extends ExecutionResult {
    lazy val toJson: String = DistributionSerializer.serializerExecutionResultJson.serialize(this)
  }

  case class TypedExecutionResult[T](result: T, stdOut: String, stdErr: String, data: Map[String, String]) extends ExecutionResult {
    lazy val toJson: String = untyped.toJson
    
    def map[O](mapValue: T => O, valueToMap: O => Map[String, String]): TypedExecutionResult[O] = {
      val newResult = mapValue(result)
      val newMap = valueToMap(newResult)
      TypedExecutionResult(newResult, stdOut, stdErr, newMap)
    }
    
  }

}
