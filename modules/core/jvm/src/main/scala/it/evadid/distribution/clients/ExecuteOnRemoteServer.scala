package it.evadid.distribution.clients

import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.core.datastructures.state.async.AsyncDataState.{AsyncDataStateFinished, AsyncDataSuccess}
import it.evadid.distribution.*
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoUntyped
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.Logger
import upickle.default.read

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class ExecuteOnRemoteServer(ip: String, port: Int) extends RemoteExecutionClient {


  private val httpClient = HttpClient.newHttpClient()
  /*

  override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = true

  override def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = Future {

    val executionInfoJson = responseData.getOrElse(
      "executionInfo",
      throw new IllegalStateException("Server response is missing 'executionInfo' field")
    )
    val res = ExecutionInfo.fromJson(executionInfoJson)
    AsyncDataSuccess(res)
  }(using ExecutionContext.global)



   */

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




