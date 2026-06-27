package it.evadid.server

import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.*
import it.evadid.util.{JvmUtils, Logger}
import play.api.libs.json.Json
import play.api.mvc.Results.*
import play.api.mvc.*
import play.api.routing.sird.*
import play.core.server.{NettyServer, ServerConfig}
import upickle.default.write

import java.io.{PrintWriter, StringWriter}
import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.*
import scala.util.{Failure, Success}

/**
 * Minimal Play-based HTTP server with dummy REST functionality.
 */
object BackendServer {

  private val serverStartedAt: LocalDateTime = LocalDateTime.now()

  private def handleExecuteCommand(bodyOption: Option[String]): Future[(Int, String)] = {
    if (bodyOption.isEmpty || bodyOption.get.isEmpty) Future.successful((400, Json.obj("error" -> "Missing request body for executeCommand").toString()))
    else {
      val promise: Promise[(Int, String)] = Promise[(Int, String)]()
      val executionCommand = ExecutionCommand.fromJson(bodyOption.get)
      val execRes = BackendCommandHandler.handleExecution(executionCommand, Logger().withPrintToStd())
      execRes.futureFirstState.onComplete {
        case Success(state) => state.match {
          case AsyncDataSuccess(exInfo) => promise.success(200, write(Map("executionInfo" -> DefaultSerializer.serializerExecutionInfoJson.serialize(exInfo.toUntyped))))
          case AsyncDataFailed(cause, additionalData) => {
            val exception = new Exception("Execution failed in BackendServer::handleExecuteCommand: " + cause.getMessage + "\nAdditional info: " + additionalData, cause )
            promise.failure(exception)
          }
        }
        case Failure(err) => promise.failure(err)
      }(using ExecutionContext.global)
      promise.future
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
      case POST(p"/executeCommand") =>
        action.async { request =>
          val bodyAsText = request.body.asText.orElse(request.body.asJson.map(_.toString))

          handleExecuteCommand(bodyAsText).map {
            case (status, responseBody) => Status(status)(responseBody).as("application/json")
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
