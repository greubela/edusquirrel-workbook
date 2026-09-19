package it.evadid.server

import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.JvmUtils
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.mvc.Results.*
import play.api.routing.sird.*
import play.core.server.{NettyServer, ServerConfig}

import java.io.{PrintWriter, StringWriter}
import java.net.InetAddress
import java.time.LocalDateTime
import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
 * Minimal Play-based HTTP server with dummy REST functionality.
 */
object BackendServer {

  private val serverStartedAt: LocalDateTime = LocalDateTime.now()

  def fail(commandReceived: LocalDateTime, msg: String, cause: Option[SerializedException], command: Option[ExecutionCommand], logger: Logger): ExecutionClientResponse = {
    logger.logError(msg)
    ExecutionClientResponse(commandReceived, commandReceived, msg, cause, command, logger.getOut(), logger.getErr())
  }

  private def handleExecuteCommand(bodyOption: Option[String], cookies: Cookies, remoteAddress: InetAddress): Future[ExecutionClientResponse] = {
    val commandReceived: LocalDateTime = LocalDateTime.now()
    val backendLogger: Logger = Logger.withNameAndPrefixes(Some(s"BackendServerLogger(Request@${commandReceived.toString})"), PrintToStdLogger.printEverything)

    val verifiedToken = AuthHandling.findAuthCookies(cookies).find(AuthHandling.isTokenValid(backendLogger, _))

    if (bodyOption.isEmpty || bodyOption.get.isEmpty)
      Future.successful(fail(commandReceived, "No Request Body Found", None, None, backendLogger))
    else ExecutionCommand.tryParse(bodyOption.get).match {
      case Failure(err) => Future.successful(fail(commandReceived, "Could not parse ExecutionCommand", Some(SerializedException(err)), None, backendLogger))
      case Success(command) => BackendCommandHandler.handleExecution(commandReceived, command, verifiedToken, remoteAddress, backendLogger)
        .recover { err => {
          fail(commandReceived, "Could not handle ExecutionCommand: " + err.getMessage, Some(SerializedException(err)), Some(command), backendLogger)
        }
        }
    }
  }

  private def handleHealth(): String = Json.obj(
    "status" -> "ok",
    "service" -> "edusquirrel-server",
    "version" -> JvmUtils.env("SERVER_VERSION").getOrElse("[unknown]"),
    "serverStartedAt" -> serverStartedAt.toString,
    "relevant api model" -> JvmUtils.env("OPENAI_MODEL").getOrElse("[unknown]")
  ).toString()


  private def buildApiRouter(action: DefaultActionBuilder): PartialFunction[RequestHeader, Handler] = {
    {

      /*case POST(p"/login") => action.async { request =>
        val bodyAsText = request.body.asText.orElse(request.body.asJson.map(_.toString))
        val remoteAddress = request.connection.remoteAddress
        val handleRes = handleLogin(bodyAsText.getOrElse(""), remoteAddress)
        val res: Future[Result] = handleRes.map {
          case Left(err) => Status(401)(err.toJson).as("application/json")
          case Right(token) => Status(200)(token.toJson).as("application/json")
        }.recover {
          err => Status(500)(SerializedException(err).toJson).as("application/json")
        }
        res
      }*/
      case POST(p"/executeCommand") =>
        action.async { request =>
          val bodyAsText = request.body.asText.orElse(request.body.asJson.map(_.toString))
          val authInfo = AuthHandling.findAuthCookies(request.cookies)
          handleExecuteCommand(bodyAsText, request.cookies, request.connection.remoteAddress).map {
            (response: ExecutionClientResponse) => {
              val (status, responseBody) = response.sendFormat
              Status(status)(responseBody).as("application/json")
            }
          }.recover {
            case err =>
              val stackWriter = StringWriter()
              println("execution err: " + err.getMessage)
              err.printStackTrace(PrintWriter(stackWriter))

              InternalServerError(Json.obj(
                "error" -> Option(err.getMessage).getOrElse(err.getClass.getName),
                "errorDetailed" -> err.getLocalizedMessage,
                "logErr" -> "missing",
                "exceptionType" -> err.getClass.getName,
                "stackTrace" -> stackWriter.toString
              ).toString()).as("application/json")
          }
        }

      case GET(p"/health") =>
        action {
          Ok(handleHealth()).as("application/json")
        }
    }
  }

  def main(args: Array[String]): Unit = {
    val port = JvmUtils.envInt("SERVER_PORT").getOrElse(9000)
    val host = JvmUtils.env("SERVER_HOSTNAME").getOrElse("[unknown]")

    println(s"[server] Booting Play HTTP server on $host:$port ...")

    val server = NettyServer.fromRouterWithComponents(ServerConfig(port = Some(port), address = "0.0.0.0")) { components =>
      import components.defaultActionBuilder as Action
      buildApiRouter(Action)
    }

    println(s"[server] Ready and serving requests at http://$host:$port")

    Runtime.getRuntime.addShutdownHook(Thread(() => {
      println("[server] Shutdown requested. Stopping Play HTTP server...")
      server.stop()
      println("[server] Server stopped.")
    }))

    Thread.currentThread().join()
  }
}
