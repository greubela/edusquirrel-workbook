package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.util.logging.Logger
import upickle.default.read

import java.net.URI
import java.net.http.*
import scala.concurrent.Future
import scala.util.Try

case class JvmRemoteExecutionClient(hostname: String, port: Int) extends RemoteExecutionClient {

  private val httpClient = HttpClient.newHttpClient()

  override protected def sendTo(logger: Logger, hostname: String, port: Int, executionCommand: ExecutionCommand): Future[Map[String, String]] = Future {
    val commandJson = executionCommand.toJson
    val request = HttpRequest
      .newBuilder(URI.create(s"http://$hostname:$port/executeCommand"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(commandJson))
      .build()

    logger.logInfo("JvmRemoteExecutionClient: finished building request")

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() != 200) {
      val message = Try(read[Map[String, String]](response.body()).getOrElse("[no error to parse]", response.body())).getOrElse(response.body())
      val msg = s"Server responded with status ${response.statusCode()}: $message"
      logger.logError(msg)
      throw new RuntimeException(msg)
    }
    try {
      read[Map[String, String]](response.body())
    } catch case e: Throwable => {
      logger.logException(e)
      throw e
    }
  }
}




