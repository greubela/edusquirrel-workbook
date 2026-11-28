package it.evadid.server.http

import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.{AdditionalLoggingOutput, ExecutionClientResponseTimestamps}
import it.evadid.server.ServerConfig
import it.evadid.server.commands.BackendCommandHandler
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.mvc.Results.*
import play.api.routing.sird.*
import upickle.default.write

import java.net.InetAddress
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/** HTTP routes: `POST /executeCommand` (with CORS for configured origins) and `GET /health`. */
final class HttpApi(config: ServerConfig, commands: BackendCommandHandler, startedAt: Instant = Instant.now()) {
  import HttpApi.*

  def routes(action: DefaultActionBuilder, parsers: PlayBodyParsers): PartialFunction[RequestHeader, Handler] = {
    val cors = corsHeaders(_, config.allowedOrigins)
    val commandParser = BodyParser("execution command") { request =>
      parsers.tolerantText(maxLength = maxCommandBodyBytes)(request).map {
        case Left(result) if result.header.status == 413 =>
          val response = fail(Instant.now(),
            s"Execution command exceeds the $maxCommandBodyBytes byte request limit. Reduce the saved history or attached data.",
            None, None, requestLogger("CommandBodyParser"))
          Left(EntityTooLarge(write(response)).as("application/json").withHeaders(cors(request)*))
        case result => result
      }(using ExecutionContext.parasitic)
    }
    {
      case OPTIONS(p"/executeCommand") =>
        action { request => NoContent.withHeaders(cors(request)*) }

      case POST(p"/executeCommand") =>
        action.async(commandParser) { request =>
          executeCommand(request.body, request.cookies, request.headers, request.connection.remoteAddress)
            .map(response => Ok(write(response)).as("application/json").withHeaders(cors(request)*))(using ExecutionContext.global)
        }

      case GET(p"/health") =>
        action(Ok(health).as("application/json"))
    }
  }

  private def executeCommand(body: String, cookies: Cookies, headers: Headers, remoteAddress: InetAddress): Future[ExecutionClientResponse] = {
    // Parsing, token verification and serialization use the CPU pool; handlers move blocking work to BlockingExecution.
    given ExecutionContext = ExecutionContext.global
    val received = Instant.now()
    val logger = requestLogger(s"BackendServerLogger(Request@$received)")
    Future {
      val verifiedToken = commands.tokens.verifiedToken(cookies, headers, logger)
      require(body.nonEmpty, "No Request Body Found")
      (ExecutionCommand.tryParse(body).get, verifiedToken)
    }.flatMap { (command, verifiedToken) =>
      commands.handleExecution(received, command, verifiedToken, remoteAddress, logger).recover { case NonFatal(error) =>
        fail(received, "Could not handle ExecutionCommand: " + error.getMessage, Some(SerializedException(error)), Some(command), logger)
      }
    }.recover { case NonFatal(error) =>
      fail(received, "Could not read ExecutionCommand: " + error.getMessage, Some(SerializedException(error)), None, logger)
    }
  }

  private def health: String = Json.obj(
    "status" -> "ok",
    "service" -> "edusquirrel-server",
    "version" -> config.version,
    "serverStartedAt" -> startedAt.toString,
    "relevant api model" -> config.llm.model
  ).toString()
}

object HttpApi {
  /** Decoded request limit; whole sessions (workbook definition and every answer history) travel in one command. */
  private[server] val maxCommandBodyBytes: Long = 100L * 1024 * 1024

  private def requestLogger(name: String): Logger = Logger.withNameAndPrefixes(Some(name), PrintToStdLogger.printEverything)

  def fail(commandReceived: Instant, msg: String, cause: Option[SerializedException], command: Option[ExecutionCommand], logger: Logger): ExecutionClientResponse = {
    logger.logError(msg)
    ExecutionClientResponse(
      ExecutionClientResponseTimestamps(commandReceived, commandReceived, Instant.now()),
      Left(cause.getOrElse(SerializedException(msg))), command,
      AdditionalLoggingOutput(logger.getOut(), logger.getErr())
    )
  }

  /** CORS headers for an allowed request origin; none otherwise, so browsers keep blocking other origins. */
  private[server] def corsHeaders(request: RequestHeader, allowedOrigins: Set[String]): Seq[(String, String)] =
    request.headers.get("Origin").filter(allowedOrigins).toSeq.flatMap(origin => Seq(
      "Access-Control-Allow-Origin" -> origin,
      "Access-Control-Allow-Credentials" -> "true",
      "Access-Control-Allow-Methods" -> "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers" -> "Content-Type, Authorization, Content-Encoding",
      "Vary" -> "Origin"))
}
