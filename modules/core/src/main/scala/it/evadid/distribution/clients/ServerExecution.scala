package it.evadid.distribution.clients

import it.evadid.distribution.{ExecutionClient, ExecutionCommand}
import upickle.default.{read, write}

import scala.concurrent.Future
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.util.Try

case class ServerExecution(ip: String, port: Int) extends ExecutionClient {

  private val httpClient = HttpClient.newHttpClient()

  // Sends command to a server with given data and executes it there (see BackendServer for a possible backend impl)

  override def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionCommand.ExecutionInfo] = Future.fromTry(Try {
    val commandJson = upickle.default.write(executionCommand)
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
    val executionInfoJson = responseData.getOrElse("executionInfo",
      throw new IllegalStateException("Server response is missing 'executionInfo' field")
    )
    read[ExecutionCommand.ExecutionInfo](executionInfoJson)
  })
  
}
