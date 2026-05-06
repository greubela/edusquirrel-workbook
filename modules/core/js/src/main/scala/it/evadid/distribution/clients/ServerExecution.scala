package it.evadid.distribution.clients

import it.evadid.distribution.ExecutionCommand
import it.evadid.distribution.executor.Executor
import org.scalajs.dom
import upickle.default.read

import scala.concurrent.Future
import scala.scalajs.js.JSON
import scala.scalajs.js.Thenable.Implicits.*

case class ServerExecution(ip: String, port: Int) extends ExecutionClient {

  val handlers: List[Executor] = List.empty

  override def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionCommand.ExecutionInfo] = {
    val commandJson = upickle.default.write(executionCommand)
    dom.fetch(
      s"http://$ip:$port/executeCommand",
      new dom.RequestInit {
        method = dom.HttpMethod.POST
        headers = JSON.parse("""{"Content-Type":"application/json"}""").asInstanceOf[dom.HeadersInit]
        body = commandJson
      }
    ).toFuture.flatMap { response =>
      response.text().toFuture.map { body =>
        if (!response.ok) {
          val message = scala.util.Try(read[Map[String, String]](body).getOrElse("error", body)).getOrElse(body)
          throw new RuntimeException(s"Server responded with status ${response.status}: $message")
        }
        val responseData = read[Map[String, String]](body)
        val executionInfoJson = responseData.getOrElse(
          "executionInfo",
          throw new IllegalStateException("Server response is missing 'executionInfo' field")
        )
        read[ExecutionCommand.ExecutionInfo](executionInfoJson)
      }
    }
  }
}
