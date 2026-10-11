package it.evadid.distribution.command

import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.{AdditionalLoggingOutput, ExecutionClientResponseTimestamps}
import upickle.default.*


/*
case class ExecutionResultUntyped(result: ujson.Value, additionalLogs: AdditionalLoggingOutput) derives ReadWriter {
  def toTyped[T: ReadWriter] = try {
    val read = read(result)
    ExecutionResult[T](read, additionalLogs)
  } catch case (err: Throwable) => {
    throw SerializedException(s"Cannot parse json value ${result} as expected type!")
  }
}


case class ExecutionResult[T](result: T, additionalLogs: AdditionalLoggingOutput) derives ReadWriter {
  def toResponse(timestamps: ExecutionClientResponseTimestamps, receivedCommand: ExecutionCommand): ExecutionClientResponse[T] = {
    ExecutionClientResponse[T](timestamps, Right(this), Some(receivedCommand), additionalLogs)
  }
}

sealed trait ExecutionResult {

  val data: Map[String, String]
  val stdOut: String
  val stdErr: String

  def toTyped[O](converter: TypeConverter[Map[String, String], O]): ExecutionResultTyped[O] = ExecutionResultTyped(converter.convertToO(data), stdOut, stdErr, data)

  def toTyped[O](converter: Map[String, String] => O): ExecutionResultTyped[O] = ExecutionResultTyped(converter.apply(data), stdOut, stdErr, data)

  lazy val untyped: ExecutionResultUntyped = ExecutionResultUntyped(data, stdOut, stdErr)

  lazy val toJson: String

}*/

object ExecutionResult {



  /*
  def apply(data: Map[String, String], stdOut: String, stdErr: String): ExecutionResult = ExecutionResultUntyped(data, stdOut, stdErr)
*/
  /*
  case class ExecutionResultUntyped(data: Map[String, String], stdOut: String, stdErr: String) extends ExecutionResult derives ReadWriter {
    lazy val toJson: String = write(this)
  }*/


}
