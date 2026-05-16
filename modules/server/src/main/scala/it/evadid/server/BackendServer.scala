package it.evadid.server

import it.evadid.core.util.io.serializer.DistributionSerializer
import it.evadid.distribution.command.*
import it.evadid.util.{JvmUtils, Logger}
import play.api.libs.json.Json
import play.api.mvc.Results.*
import play.api.mvc.{DefaultActionBuilder, Handler, RequestHeader}
import play.api.routing.sird.*
import play.core.server.{NettyServer, ServerConfig}
import upickle.default.{read, write}

import java.time.LocalDateTime
import java.io.{PrintWriter, StringWriter}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

private val corsHeaders: Seq[(String, String)] = Seq(
  "Access-Control-Allow-Origin"  -> "*",
  "Access-Control-Allow-Methods" -> "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers" -> "Content-Type"
)

/**
 * Minimal Play-based HTTP server with dummy REST functionality.
 */
object BackendServer {

  private val serverStartedAt: LocalDateTime = LocalDateTime.now()

  private def withCors(result: play.api.mvc.Result): play.api.mvc.Result =
    result.withHeaders(corsHeaders*)

  /**
   * Handles POST /api/llm/complete.
   *
   * Accepts two payload shapes:
   *   { prompt, systemPrompt? }                             (simple)
   *   { llmPrompt: { workbookPrompt, exercisePrompt,
   *                  studentAnswer, systemPrompt? } }       (structured)
   * Returns: text/plain with the generated text.
   */
  private def handleLlmComplete(bodyOption: Option[String]): Future[(Int, String)] =
    bodyOption match
      case None | Some("") => Future.successful((400, "Missing request body"))
      case Some(body) =>
        val json = Json.parse(body)
        val (prompt, systemPrompt) = (json \ "llmPrompt").asOpt[play.api.libs.json.JsObject] match
          case Some(lp) =>
            val workbook = (lp \ "workbookPrompt").asOpt[String].getOrElse("")
            val exercise = (lp \ "exercisePrompt").asOpt[String].getOrElse("")
            val student  = (lp \ "studentAnswer").asOpt[String].getOrElse("")
            val sys      = (lp \ "systemPrompt").asOpt[String].getOrElse("")
            (s"[Teacher]:\n$workbook\n\n$exercise\n\n[Student]:\n$student".trim, sys)
          case None =>
            ((json \ "prompt").asOpt[String].getOrElse(""),
             (json \ "systemPrompt").asOpt[String].getOrElse(""))
        if prompt.isEmpty then Future.successful((400, "Missing 'prompt' (or 'llmPrompt') in request."))
        else CompleteChatWithLLMCommand.completePlain(prompt, systemPrompt).map(text => (200, text))

  private def handleExecuteCommand(bodyOption: Option[String]): Future[(Int, String)] = {

    if (bodyOption.isEmpty || bodyOption.get.isEmpty) Future.successful((400, Json.obj("error" -> "Missing request body for executeCommand").toString()))
    else {
      val promise: Promise[(Int, String)] = Promise[(Int, String)]()
      val executionCommand = ExecutionCommand.fromJson(bodyOption.get)
      BackendCommandHandler.handleExecution(executionCommand, Logger()).map {
        case e: ExecutionInfo => (200, write(Map("executionInfo" -> DistributionSerializer.serializerExecutionInfoJson.serialize(e))))
        case _ => (500, write(Map("error" -> "unknown error in server :/")))
      }(using ExecutionContext.global).onComplete(promise.complete)
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
              err.printStackTrace(PrintWriter(stackWriter))
              InternalServerError(Json.obj(
                "error" -> Option(err.getMessage).getOrElse(err.getClass.getName),
                "exceptionType" -> err.getClass.getName,
                "stackTrace" -> stackWriter.toString
              ).toString()).as("application/json")
          }
        }

      case GET(p"/health") =>
        action {
          Ok(handleHealth()).as("application/json")
        }

      case OPTIONS(p"/api/llm/complete") =>
        action { _ =>
          withCors(NoContent)
        }

      case POST(p"/api/llm/complete") =>
        action.async { request =>
          val body = request.body.asText.orElse(request.body.asJson.map(_.toString))
          handleLlmComplete(body).map {
            case (200, text) => withCors(Ok(text).as("text/plain; charset=utf-8"))
            case (status, msg) => withCors(Status(status)(msg).as("text/plain; charset=utf-8"))
          }.recover {
            case err => withCors(InternalServerError(s"[ERROR]: ${err.getMessage}").as("text/plain; charset=utf-8"))
          }
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
