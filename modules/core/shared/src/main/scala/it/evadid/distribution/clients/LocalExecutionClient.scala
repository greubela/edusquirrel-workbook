package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.core.util.io.ParsingContext
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

  private def decodeRequest(command: ExecutionCommand): Try[I] = Try {
    val safe = command.stripPrivacySensitiveInformation().json
    try read[I](command.json)
    catch case scala.util.control.NonFatal(error) =>
      // Parser messages can echo rejected values. Apply the command's existing
      // privacy policy to the entire diagnostic chain, not just its preview.
      def strings(value: ujson.Value): Set[String] = value match {
        case obj: ujson.Obj => obj.value.values.flatMap(strings).toSet
        case arr: ujson.Arr => arr.value.flatMap(strings).toSet
        case ujson.Str(text) => Set(text)
        case _ => Set.empty
      }
      val hidden = (strings(command.json) -- strings(safe)).filter(_.nonEmpty).toList.sortBy(-_.length)
      def clean(text: String): String = hidden.foldLeft(text)((message, secret) =>
        message.replace(ujson.write(secret), "[redacted]").replace(secret, "[redacted]"))
      def sanitize(error: SerializedException): SerializedException =
        error.copy(msg = clean(error.msg), cause = error.cause.map(sanitize))
      val cause = sanitize(SerializedException(error))
      throw SerializedException(
        s"Unsupported request format for command ${command.name}: could not parse '${ParsingContext.preview(safe.toString)}' because: ${ParsingContext.reason(cause)}. Client and server must use the same version.", cause)
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
