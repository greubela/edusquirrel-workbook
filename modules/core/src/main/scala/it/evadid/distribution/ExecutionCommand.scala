package it.evadid.distribution

import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}
import upickle.default.*

case class ExecutionCommand(name: String, params: Map[String, String]) {

}

object ExecutionCommand {

  case class ExecutionInfo(command: ExecutionCommand, result: Try[ExecutionResult], meta: Option[ExecutionHistory])

  case class ExecutionResult(
                              data: Map[String, String],
                              stdOut: String,
                              stdErr: String
                            )

  case class ExecutionHistory(
                             timestampCommandRequested: LocalDateTime,
                             timestampCommandReceived: LocalDateTime,
                             timestampExecutionStarted: LocalDateTime,
                             timestampExecutionFinished: LocalDateTime,
                           )

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

  given ReadWriter[ExecutionCommand] = macroRW
  given ReadWriter[ExecutionResult] = macroRW
  given ReadWriter[ExecutionHistory] = macroRW
  given ReadWriter[ExecutionInfo] = macroRW

}
