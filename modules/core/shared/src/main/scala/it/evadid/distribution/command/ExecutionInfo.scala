package it.evadid.distribution.command

import it.evadid.core.util.io.TypeConverter
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionInfo.{ExecutionInfoTyped, ExecutionInfoUntyped}
import it.evadid.distribution.command.ExecutionResult.ExecutionResultTyped

import java.time.LocalDateTime


sealed trait ExecutionInfo {

  val command: ExecutionCommand
  val result: ExecutionResult
  val history: ExecutionHistory

  def toJson: String = DefaultSerializer.serializerExecutionInfoJson.serialize(this.toUntyped)

  def toTyped[O](converter: TypeConverter[Map[String, String], O]): ExecutionInfoTyped[O] = ExecutionInfoTyped(command, result, history, result.toTyped(converter))

  def toTyped[O](converter: Map[String, String] => O): ExecutionInfoTyped[O] = ExecutionInfoTyped[O](command, result, history, result.toTyped(converter))

  def toUntyped: ExecutionInfoUntyped = ExecutionInfoUntyped(command, result, history)

  def withFixedTime(timeRequested: LocalDateTime, timeReceived: LocalDateTime): ExecutionInfoUntyped = ExecutionInfoUntyped(command, result, history.withFixedTime(timeRequested, timeReceived))
}

object ExecutionInfo {

  def fromJson(json: String): ExecutionInfo = DefaultSerializer.serializerExecutionInfoJson.deserialize(json)

  case class ExecutionInfoUntyped(command: ExecutionCommand, result: ExecutionResult, history: ExecutionHistory) extends ExecutionInfo{
    override val toUntyped: ExecutionInfoUntyped = this
  }

  case class ExecutionInfoTyped[T](command: ExecutionCommand, result: ExecutionResult, history: ExecutionHistory, resultTyped: ExecutionResultTyped[T]) extends ExecutionInfo {
  }

  case class ExecutionFailure(command: ExecutionCommand, historyOp: Option[ExecutionHistory], error: SerializedException) extends Throwable(s"Command ${command.name} failed with error: ${error.msg}") {
    override val toString: String = s"ExecutionFailure(command=${command.name}, error=${error.msg}, history=${historyOp.map(_.toString).getOrElse("None")}"
  }

}
/*

sealed trait ExecutionInfo2 {

  def command: ExecutionCommand

  def resultOp: Option[ExecutionResult]

  def errorOp: Option[SerializedException]

  def historyOp: Option[ExecutionHistory]

  def resultEither: Either[ExecutionResult, SerializedException] =
    if (resultOp.nonEmpty) Left(resultOp.get)
    else if (errorOp.nonEmpty) Right(errorOp.get)
    else Right(SerializedException(new IllegalStateException("ExecutionInfoResult had neither a result nor an error")))

  def toJson: String

}



object ExecutionInfo {

  def fromJson(json: String): ExecutionInfoUntyped = {
    DistributionSerializer.serializerExecutionInfoJson.deserialize(json)
  }

  sealed trait ExecutionInfoSuccess extends ExecutionInfo {

    def result: ExecutionResult

    def history: ExecutionHistory

    override def errorOp: Option[SerializedException] = None

    override def historyOp: Option[ExecutionHistory] = Some(history)

    override def resultOp: Option[ExecutionResult] = Some(result)
  }

  sealed trait ExecutionInfoFailure extends ExecutionInfo {
    def error: SerializedException

    def errorOp: Option[SerializedException] = Some(error)

    def resultOp: Option[ExecutionResult] = None
  }

  sealed trait ExecutionInfoUntyped extends ExecutionInfo {
    def toTyped[O](converter: TypeConverter[Map[String, String], O]): ExecutionInfoTyped[O]

    def toTyped[O](converter: Map[String, String] => O): ExecutionInfoTyped[O] //=

    def withFixedTime(timeRequested: LocalDateTime, timeReceived: LocalDateTime): ExecutionInfoUntyped
  }

  sealed trait ExecutionInfoTyped[T] extends ExecutionInfo {
    def resultTypedOp: Option[ExecutionResultTyped[T]]

    def resultTypedEither: Either[ExecutionResultTyped[T], Throwable] =
      if (resultTypedOp.nonEmpty) Left(resultTypedOp.get)
      else if (errorOp.nonEmpty) Right(errorOp.get)
      else Right(IllegalStateException("ExecutionInfoResult had neither a result nor an error"))

    // resultTypedTry todo (analog oben)

    def map[O](mapValue: T => O, valueToMap: O => Map[String, String]): ExecutionInfoTyped[O]

    def toUntyped: ExecutionInfoUntyped

  }

  def apply(command: ExecutionCommand, result: Try[ExecutionResult], meta: Option[ExecutionHistory] = None): ExecutionInfoUntyped = result.match {
    case Success(executionResult: ExecutionResult) if meta.nonEmpty => ExecutionInfoSuccessUntyped(command, meta.get, executionResult)
    case Success(executionResult: ExecutionResult) => ExecutionInfoFailureUntyped(command, meta, SerializedException(Exception("ExecutionInfo was calcualted correctly but had no meta data!")))
    case Failure(err) => ExecutionInfoFailureUntyped(command, meta, SerializedException(err))
  }

  // def fromJson(json: String): ExecutionInfo = DistributionSerializer.serializerExecutionInfoJson.deserialize(json)


  case class ExecutionInfoSuccessUntyped(command: ExecutionCommand, history: ExecutionHistory, result: ExecutionResult) extends ExecutionInfoUntyped with ExecutionInfoSuccess {


    override def toJson: String = ""
  }

  case class ExecutionInfoSuccessTyped[T](success: ExecutionInfoSuccessUntyped, resultTyped: ExecutionResultTyped[T]) extends ExecutionInfoTyped[T] {

    def toUntyped: ExecutionInfoUntyped = success

    def map[O](mapValue: T => O, valueToMap: O => Map[String, String]): ExecutionInfoTyped[O] = ExecutionInfoSuccessTyped[O](success, resultTyped.map(mapValue, valueToMap))

    override def toJson: String = toUntyped.toJson

    override def resultTypedOp: Option[ExecutionResultTyped[T]] = Some(resultTyped)

    override def command: ExecutionCommand = success.command

    override def resultOp: Option[ExecutionResult] = success.resultOp

    override def errorOp: Option[SerializedException] = success.errorOp

    override def historyOp: Option[ExecutionHistory] = success.historyOp
  }



  case class ExecutionInfoFailureTyped[T](base: ExecutionInfoFailureUntyped) extends ExecutionInfoTyped[T] with ExecutionInfoFailure {

    override def map[O](mapValue: T => O, valueToMap: O => Map[String, String]): ExecutionInfoTyped[O] = ExecutionInfoFailureTyped[O](base)

    override def command: ExecutionCommand = base.command

    override def resultOp: Option[ExecutionResult] = base.resultOp

    override def errorOp: Option[SerializedException] = base.errorOp

    override def historyOp: Option[ExecutionHistory] = base.historyOp

    override def resultTypedOp: Option[ExecutionResultTyped[T]] = None

    override def toUntyped: ExecutionInfoUntyped = base

    override def error: SerializedException = base.error

    override def toJson: String = base.toJson
  }


}*/