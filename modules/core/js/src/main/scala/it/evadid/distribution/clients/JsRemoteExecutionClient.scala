package it.evadid.distribution.clients

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.logging.Logger
import org.scalajs.dom
import org.scalajs.dom.{RequestCredentials, Response}
import upickle.default.*

import scala.concurrent.Future
import scala.scalajs.js

private case class JsRemoteExecutionClient(hostname: String, port: Int, token: Option[SignedToken]) extends RemoteExecutionClient derives ReadWriter {

  def sendCommand(logger: Logger, executionCommand: ExecutionCommand, pDest: String, pBody: String): Future[Response] = {
    logger.logInfo("JsRemoteExecutionClient: sent request and waiting for a response")
    dom.fetch(
      pDest,
      new dom.RequestInit {
        method = dom.HttpMethod.POST
        headers = js.Dictionary(
          "Content-Type" -> "application/json",
          SignedToken.cookieKey -> token.map(_.toJson).getOrElse("")
        ).asInstanceOf[dom.HeadersInit]
        body = pBody
        credentials = RequestCredentials.include
      }
    ).toFuture
  }


  def handleResponse(logger: Logger, response: Response): Future[ExecutionClientResponse] = {
    logger.logInfo(s"JsRemoteExecutionClient: received a response (code ${response.status})!")
    if (!response.ok) Future.failed(SerializedException(s"Server responded with status ${response.status}: ${response.statusText}"))
    response.text().toFuture.map(text => handleText(logger, text))
  }

  def handleText(logger: Logger, text: String): ExecutionClientResponse = read(text)(using ExecutionClientResponse.derived$ReadWriter)

  override protected def sendTo(logger: Logger, ip: String, port: Int, executionCommand: ExecutionCommand): Future[ExecutionClientResponse] = {
    val body = write(executionCommand)
    val dest = if (hostname.startsWith("http")) s"$hostname:$port/executeCommand" else s"https://$hostname:$port/executeCommand"

    sendCommand(logger, executionCommand, dest, body).flatMap(response => handleResponse(logger, response))
  }

  /*
  override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = true

  override def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = {
    val requested = LocalDateTime.now()
    val commandJson = executionCommand.toJson
    val dest = if (hostname.startsWith("http")) {
      s"$hostname:$port/executeCommand"
    } else {
      s"https://$hostname:$port/executeCommand"
    }

    val future = dom.fetch(
      dest,
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
        val serverReceived = ExecutionInfo.fromJson(executionInfoJson)
        val timeFixed = serverReceived.withFixedTime(requested, requested)
        timeFixed
      }
    }
    future.map((futVal: ExecutionInfoUntyped) => AsyncDataSuccess(futVal))
  }*/


}
