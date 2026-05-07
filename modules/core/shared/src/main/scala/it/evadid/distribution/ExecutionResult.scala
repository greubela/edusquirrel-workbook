package it.evadid.distribution

import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}
import upickle.default.*

case class ExecutionResult(
                            data: Map[String, String],
                            stdOut: String,
                            stdErr: String
                          )
