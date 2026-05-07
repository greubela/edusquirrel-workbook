package it.evadid.distribution

import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}
import upickle.default.*



case class ExecutionInfo(command: ExecutionCommand, result: Try[ExecutionResult], meta: Option[ExecutionHistory])