package it.evadid.distribution.clients


import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.{AdditionalLoggingOutput, ExecutionClientResponseTimestamps}
import it.evadid.util.logging.Logger
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.util.*

/**
 * An execution client that executes commands locally (and, as such, builds the ExecutionInfo itself)
 */
trait LocalExecutionClient[I: ReadWriter, O: ReadWriter] extends ExecutionClient {

  protected def rawExecution(logger: Logger, input: I): Future[O]

  private[clients] override def handleCommandExecution(logger: Logger, executionCommand: ExecutionCommand): Future[ExecutionClientResponse] = {
    val recv = LocalDateTime.now()
    val start = LocalDateTime.now()
    val input: I = try {
      read(executionCommand.json)
    } catch case (err: Throwable) => {
      throw SerializedException(s"${this.getClass.getSimpleName} could not parse ${executionCommand.json} as input!")
    }
    rawExecution(logger, input).transform {
      case Success(res) => Try {
        val finish = LocalDateTime.now()
        val timestamps = ExecutionClientResponseTimestamps(recv, start, finish)
        ExecutionClientResponse.forValue[O](timestamps, res, executionCommand)
      }
      case Failure(err) => Try {
        val finish = LocalDateTime.now()
        val timestamps = ExecutionClientResponseTimestamps(recv, start, finish)
        ExecutionClientResponse.forError(timestamps, SerializedException(err), executionCommand)
      }
    }
  }


}
