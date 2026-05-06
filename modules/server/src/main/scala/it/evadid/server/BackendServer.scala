package it.evadid.server

import it.evadid.distribution.*
import it.evadid.distribution.ExecutionCommand.ExecutionInfo
import upickle.default.{read, write}

import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.DefaultActionBuilder
import play.api.mvc.Results.Status
import play.api.mvc.Results.*
import play.api.mvc.{Handler, RequestHeader}
import play.api.routing.sird.*
import play.core.server.{NettyServer, ServerConfig}

/**
 * Minimal Play-based HTTP server with dummy REST functionality.
 */
object BackendServer extends ExecutionServer {


  private val dummyExecutor: Executor = new Executor {
    override def canExecute(executionCommand: ExecutionCommand): Boolean = true

    override def execute(executionCommand: ExecutionCommand): Option[ExecutionInfo] = {
      val now = LocalDateTime.now()
      val result = ExecutionCommand.ExecutionResult(
        data = executionCommand.params,
        stdOut = s"Dummy executed command '${executionCommand.name}'",
        stdErr = ""
      )
      Some(ExecutionInfo(
        command = executionCommand,
        result = Success(result),
        meta = Some(
          ExecutionCommand.CommandHistory(
            timestampCommandRequested = now,
            timestampCommandReceived = now,
            timestampExecutionStarted = now,
            timestampExecutionFinished = now
          )
        )
      ))
    }
  }

  var executors: List[Executor] = List(dummyExecutor)

  override def getExecutor: Executor = new Executor {
    override def canExecute(executionCommand: ExecutionCommand): Boolean =
      executors.exists(_.canExecute(executionCommand))

    override def execute(executionCommand: ExecutionCommand): Option[ExecutionInfo] =
      executors.find(_.canExecute(executionCommand)).flatMap(_.execute(executionCommand))
  }

  def onExecuteCommandReceived(rawCommand: String): ExecutionInfo = {
    val executionCommand = read[ExecutionCommand](rawCommand)
    if (executionCommand.name.trim.isEmpty) {
      throw new IllegalArgumentException("ExecutionCommand.name must not be empty")
    }
    getExecutor.execute(executionCommand).getOrElse {
      throw new IllegalStateException(s"No executor available for command '${executionCommand.name}'")
    }
  }

  private def handleExecuteCommand(rawBody: Option[String]): (Int, String) = {
    rawBody match {
      case Some(rawCommand) if rawCommand.nonEmpty =>
        Try(onExecuteCommandReceived(rawCommand)) match {
          case Success(executionInfo) if executionInfo.result.isSuccess =>
            (200, write(Map("executionInfo" -> write(executionInfo))))
          case Success(executionInfo) =>
            (500, Json.obj("error" -> executionInfo.result.failed.get.getMessage).toString())
          case Failure(exception) =>
            (400, Json.obj("error" -> exception.getMessage).toString())
        }
      case _ =>
        (400, Json.obj("error" -> "Missing request body for executeCommand").toString())
    }
  }

  private def handleHealth(): String = Json.obj("status" -> "ok", "service" -> "edusquirrel-server").toString()

  private def handleItems(): String = Json.obj(
    "items" -> Json.arr(
      Json.obj("id" -> 1, "name" -> "dummy-pencil"),
      Json.obj("id" -> 2, "name" -> "dummy-notebook")
    )
  ).toString()

  private def handleItem(id: String): String = Json.obj("id" -> id, "name" -> s"dummy-item-$id").toString()

  private def handleCreateItem(payload: JsValue): String =
    Json.obj("message" -> "dummy item created", "payload" -> payload).toString()

  private def buildApiRouter(action: DefaultActionBuilder): PartialFunction[RequestHeader, Handler] = {
    {
      case POST(p"/executeCommand") =>
        action { request =>
          val bodyAsText = request.body.asText.orElse(request.body.asJson.map(_.toString))
          val (status, responseBody) = handleExecuteCommand(bodyAsText)
          Status(status)(responseBody).as("application/json")
        }

      case GET(p"/health") =>
        action {
          Ok(handleHealth()).as("application/json")
        }

      case GET(p"/api/items") =>
        action {
          Ok(handleItems()).as("application/json")
        }

      case GET(p"/api/items/$id") =>
        action {
          Ok(handleItem(id)).as("application/json")
        }

      case POST(p"/api/items") =>
        action { request =>
          val payload: JsValue = request.body.asJson.getOrElse(Json.obj("raw" -> request.body.toString))
          Created(handleCreateItem(payload)).as("application/json")
        }
    }
  }


  def main(args: Array[String]): Unit = {
    val port = args.headOption.flatMap(_.toIntOption).getOrElse(9000)

    println(s"[server] Booting Play HTTP server on 0.0.0.0:$port ...")

    val server = NettyServer.fromRouterWithComponents(ServerConfig(port = Some(port), address = "0.0.0.0")) { components =>
      import components.defaultActionBuilder as Action
      buildApiRouter(Action)
    }

    println(s"[server] Ready and serving requests at http://0.0.0.0:$port")

    Runtime.getRuntime.addShutdownHook(Thread(() => {
      println("[server] Shutdown requested. Stopping Play HTTP server...")
      server.stop()
      println("[server] Server stopped.")
    }))

    Thread.currentThread().join()
  }
}
