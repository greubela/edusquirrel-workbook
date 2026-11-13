package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.ExecutionClientResponseTimestamps
import it.evadid.util.logging.Logger
import upickle.default.*
import java.time.LocalDateTime
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/** Executes a typed local command and builds its transport response. */
trait LocalExecutionClient[I: ReadWriter, O: ReadWriter] extends ExecutionClient {
  protected def rawExecution(logger: Logger, input: I): Future[O]

  private def decodeRequest(command: ExecutionCommand): Try[I] = Try(read[I](command.json)).recoverWith {
    case _ => Failure(SerializedException(s"Unsupported request format for command ${command.name}. Rebuild and reload the client and restart the server using the same version. Request with secrets removed: ${command.stripPrivacySensitiveInformation().json}"))
  }

  private[clients] override def handleCommandExecution(logger: Logger, command: ExecutionCommand): Future[ExecutionClientResponse] = {
    val received = LocalDateTime.now()
    val started = LocalDateTime.now()
    decodeRequest(command) match {
      case Failure(error) => Future.failed(error)
      case Success(input) =>
        val execution = Try(rawExecution(logger, input)).fold(Future.failed, identity)
        execution.transform { outcome =>
          Try {
            val timestamps = ExecutionClientResponseTimestamps(received, started, LocalDateTime.now())
            outcome.fold(
              error => ExecutionClientResponse.forError(timestamps, SerializedException(error), command),
              value => ExecutionClientResponse.forValue(timestamps, value, command))
          }
        }
    }
  }
}
