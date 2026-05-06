package it.evadid.server

import it.evadid.distribution.*
import it.evadid.distribution.ExecutionCommand.ExecutionInfo

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Results.*
import play.api.routing.sird.*
import play.core.server.{NettyServer, ServerConfig}

/**
 * Minimal Play-based HTTP server with dummy REST functionality.
 */
object BackendServer extends ExecutionServer {


  val getExecutor: Executor = ??? // todo: implement later


  def main(args: Array[String]): Unit = {
    val port = args.headOption.flatMap(_.toIntOption).getOrElse(9000)

    println(s"[server] Booting Play HTTP server on 0.0.0.0:$port ...")

    val server = NettyServer.fromRouterWithComponents(
      ServerConfig(port = Some(port), address = "0.0.0.0")
    ) { components =>
      import components.defaultActionBuilder as Action
      {
        case GET(p"/executeCommand") =>
          Action {
            ??? // somehow call a method onExecutionCommandReceived with relevant info that calls the executor and then send back results.
            Ok(Json.obj("status" -> "ok", "service" -> "edusquirrel-server"))
          }

        case GET(p"/health") =>
          Action {
            Ok(Json.obj("status" -> "ok", "service" -> "edusquirrel-server"))
          }

        case GET(p"/api/items") =>
          Action {
            Ok(
              Json.obj(
                "items" -> Json.arr(
                  Json.obj("id" -> 1, "name" -> "dummy-pencil"),
                  Json.obj("id" -> 2, "name" -> "dummy-notebook")
                )
              )
            )
          }

        case GET(p"/api/items/$id") =>
          Action {
            Ok(Json.obj("id" -> id, "name" -> s"dummy-item-$id"))
          }

        case POST(p"/api/items") =>
          Action { request =>
            val payload: JsValue = request.body.asJson.getOrElse(Json.obj("raw" -> request.body.toString))
            Created(Json.obj("message" -> "dummy item created", "payload" -> payload))
          }
      }
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

