package it.evadid.distribution

import java.time.LocalDateTime
import scala.util.Try

case class ExecutionCommand(name: String, params: Map[String, String]) {

}

object ExecutionCommand {

  case class ExecutionInfo(command: ExecutionCommand, result: Try[ExecutionResult], meta: Option[CommandHistory])

  case class ExecutionResult(
                              data: Map[String, String],
                              stdOut: String,
                              stdErr: String
                            )

  case class CommandHistory(
                             timestampCommandRequested: LocalDateTime,
                             timestampCommandReceived: LocalDateTime,
                             timestampExecutionStarted: LocalDateTime,
                             timestampExecutionFinished: LocalDateTime,
                           )

}
