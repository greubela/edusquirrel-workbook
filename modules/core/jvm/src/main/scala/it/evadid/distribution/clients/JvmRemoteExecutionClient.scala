package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import upickle.default.read

import java.net.URI
import java.net.http.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class JvmRemoteExecutionClient(ip: String, port: Int) extends RemoteExecutionClient {

  private val httpClient = HttpClient.newHttpClient()

  override protected def sendTo(ip: String, port: Int, executionCommand: ExecutionCommand): Future[ExecutionClientResponse] = Future {
    val commandJson = executionCommand.toJson
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
    ExecutionClientResponse.apply(responseData)
  }(using ExecutionContext.global)
}




