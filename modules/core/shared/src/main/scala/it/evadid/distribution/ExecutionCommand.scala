package it.evadid.distribution

import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.distribution.ExecutionCommand.BasicExecutionCommand
import it.evadid.distribution.executor.ExecutableCommand
import upickle.default.*

import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}

trait ExecutionCommand {
  def name: String

  def params: Map[String, String]

  def toBasic: BasicExecutionCommand = BasicExecutionCommand(name, params)
  
  def makeExecutable[T](executionHandler: T => ExecutionResult, typeConverter: TypeConverter[Map[String, String], T]): ExecutableCommand[T] =
    ExecutableCommand(name, typeConverter, executionHandler)
}

object ExecutionCommand {

  case class BasicExecutionCommand(name: String, params: Map[String, String]) extends ExecutionCommand {
  }

  def apply(name: String, params: Map[String, String]): ExecutionCommand = BasicExecutionCommand(name, params)

  given ReadWriter[LocalDateTime] =
    readwriter[String].bimap[LocalDateTime](_.toString, LocalDateTime.parse)

  given [T: ReadWriter]: ReadWriter[Try[T]] =
    readwriter[ujson.Value].bimap[Try[T]](
      {
        case Success(value) => ujson.Obj("success" -> writeJs(value))
        case Failure(exception) => ujson.Obj("failure" -> exception.getMessage)
      },
      json =>
        json.obj.get("success") match {
          case Some(success) => Success(read[T](success))
          case None => Failure(new RuntimeException(json.obj.get("failure").map(_.str).getOrElse("Unknown failure")))
        }
    )

  given ReadWriter[ExecutionCommand] = new Serializer[ExecutionCommand]() {
    override def serialize(obj: ExecutionCommand): String = write(obj.toBasic)(using bec)

    override def deserialize(str: String): ExecutionCommand = read(str)(using bec)
  }.uPickleReadWrite

  given bec : ReadWriter[BasicExecutionCommand] = macroRW

  given ReadWriter[ExecutionResult] = macroRW

  given ReadWriter[ExecutionHistory] = macroRW

  given ReadWriter[ExecutionInfo] = macroRW


}
