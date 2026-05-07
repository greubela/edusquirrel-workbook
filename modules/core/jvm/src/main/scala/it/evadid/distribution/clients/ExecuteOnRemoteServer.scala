package it.evadid.distribution.clients

import it.evadid.distribution.{ExecutionCommand, ExecutionInfo}
import it.evadid.distribution.executor.Executor
import upickle.default.read

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class ExecuteOnRemoteServer(ip: String, port: Int) extends ExecutionClient {

  private val httpClient = HttpClient.newHttpClient()

  val handlers: List[Executor] = List.empty

  override def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionInfo] = Future {
    val commandJson = upickle.default.write(executionCommand)(using ExecutionCommand.given_ReadWriter_ExecutionCommand)
    val request = HttpRequest
      .newBuilder(URI.create(s"http://$ip:$port/executeCommand"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(commandJson))
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() != 200) {
      val message = Try(read[Map[String, String]](response.body()).getOrElse("error", response.body())).getOrElse(response.body())
      throw new RuntimeException(s"Server responded with status ${response.statusCode()}: $message")
    }
    val responseData = read[Map[String, String]](response.body())
    val executionInfoJson = responseData.getOrElse(
      "executionInfo",
      throw new IllegalStateException("Server response is missing 'executionInfo' field")
    )
    read[ExecutionInfo](executionInfoJson)(using ExecutionCommand.given_ReadWriter_ExecutionInfo)
  }(using ExecutionContext.global)
}
