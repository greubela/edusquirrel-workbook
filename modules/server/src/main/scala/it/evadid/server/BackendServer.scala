package it.evadid.server

import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.{AdditionalLoggingOutput, ExecutionClientResponseTimestamps}
import upickle.default.write
import it.evadid.util.JvmUtils
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.mvc.Results.*
import play.api.routing.sird.*
import play.api.routing.Router
import play.api.BuiltInComponents
import play.api.NoHttpFiltersComponents
import play.core.server.{NettyServerComponents, Server, ServerConfig}

import java.net.InetAddress
import java.time.LocalDateTime
import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

/**
 * Minimal Play-based HTTP server with dummy REST functionality.
 */
object BackendServer {

  private val serverStartedAt: LocalDateTime = LocalDateTime.now()

  def fail(commandReceived: LocalDateTime, msg: String, cause: Option[SerializedException], command: Option[ExecutionCommand], logger: Logger): ExecutionClientResponse = {
    logger.logError(msg)
    ExecutionClientResponse(
      ExecutionClientResponseTimestamps(commandReceived, commandReceived, LocalDateTime.now()),
      Left(cause.getOrElse(SerializedException(msg))), command,
      AdditionalLoggingOutput(logger.getOut(), logger.getErr())
    )
  }

  private def handleExecuteCommand(bodyOption: Option[String], cookies: Cookies, headers: Headers, remoteAddress: InetAddress): Future[ExecutionClientResponse] = {
    val commandReceived: LocalDateTime = LocalDateTime.now()
    val backendLogger: Logger = Logger.withNameAndPrefixes(Some(s"BackendServerLogger(Request@${commandReceived.toString})"), PrintToStdLogger.printEverything)

    Future {
      val verifiedToken = AuthHandling.findAuthCookies(cookies, headers).find(AuthHandling.isTokenValid(backendLogger, _))
      val command = bodyOption.filter(_.nonEmpty).getOrElse(throw IllegalArgumentException("No Request Body Found"))
      (ExecutionCommand.tryParse(command).get, verifiedToken)
    }.flatMap { (command, verifiedToken) =>
      Future(BackendCommandHandler.handleExecution(commandReceived, command, verifiedToken, remoteAddress, backendLogger)).flatten
        .recover { case NonFatal(error) =>
          fail(commandReceived, "Could not handle ExecutionCommand: " + error.getMessage,
            Some(error match {
              case serialized: SerializedException => serialized
              case other => SerializedException(other)
            }), Some(command), backendLogger)
        }
    }.recover { case NonFatal(error) =>
      fail(commandReceived, "Could not read ExecutionCommand: " + error.getMessage,
        Some(SerializedException(error)), None, backendLogger)
    }
  }

  private def handleHealth(): String = Json.obj(
    "status" -> "ok",
    "service" -> "edusquirrel-server",
    "version" -> JvmUtils.env("SERVER_VERSION").getOrElse("[unknown]"),
    "serverStartedAt" -> serverStartedAt.toString,
    "relevant api model" -> JvmUtils.env("OPENAI_MODEL").getOrElse("[unknown]")
  ).toString()


  private[server] val maxCommandBodyBytes: Long = 16L * 1024 * 1024

  private[server] def buildApiRouter(action: DefaultActionBuilder, parsers: PlayBodyParsers): PartialFunction[RequestHeader, Handler] = {
    val commandParser = BodyParser("execution command") { request =>
      parsers.tolerantText(maxLength = maxCommandBodyBytes)(request).map {
        case Left(result) if result.header.status == 413 =>
          val logger = Logger.withNameAndPrefixes(Some("CommandBodyParser"), PrintToStdLogger.printEverything)
          val response = fail(LocalDateTime.now(),
            s"Execution command exceeds the $maxCommandBodyBytes byte request limit. Reduce the saved history or attached data.",
            None, None, logger)
          Left(EntityTooLarge(write(response)).as("application/json"))
        case result => result
      }
    }
    {
      case POST(p"/executeCommand") =>
        action.async(commandParser) { request =>
          handleExecuteCommand(Some(request.body), request.cookies, request.headers, request.connection.remoteAddress)
            .map(response => Ok(write(response)).as("application/json"))
        }


      case GET(p"/health") =>
        action {
          Ok(handleHealth()).as("application/json")
        }
    }
  }

  private[server] def createServer(config: ServerConfig)(routes: (DefaultActionBuilder, PlayBodyParsers) => PartialFunction[RequestHeader, Handler]): Server =
    new NettyServerComponents with BuiltInComponents with NoHttpFiltersComponents {
      override lazy val serverConfig: ServerConfig = config
      override lazy val configuration: play.api.Configuration = config.configuration

      override def router: Router = Router.from(routes(defaultActionBuilder, playBodyParsers))
    }.server

  def main(args: Array[String]): Unit = {
    val port = JvmUtils.envInt("SERVER_PORT").getOrElse(9000)
    val host = JvmUtils.env("SERVER_HOSTNAME").getOrElse("[unknown]")

    println(s"[server] Booting Play HTTP server on $host:$port ...")

    //it.evadid.server.database.EnsureDatabaseProperlySetup.onStartup(Logger.withNameAndPrefixes(Some("DatabaseSetup"), PrintToStdLogger.printEverything))

    val server = createServer(ServerConfig(port = Some(port), address = "0.0.0.0"))(buildApiRouter)

    println(s"[server] Ready and serving requests at http://$host:$port")

    Runtime.getRuntime.addShutdownHook(Thread(() => {
      println("[server] Shutdown requested. Stopping Play HTTP server...")
      server.stop()
      println("[server] Server stopped.")
    }))

    Thread.currentThread().join()
  }
}
