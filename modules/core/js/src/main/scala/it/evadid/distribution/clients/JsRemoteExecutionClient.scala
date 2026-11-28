package it.evadid.distribution.clients

import it.evadid.core.util.UtcTime

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.logging.Logger
import org.scalajs.dom
import org.scalajs.dom.{RequestCredentials, Response}
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.scalajs.js
import scala.util.control.NonFatal

private case class JsRemoteExecutionClient(hostname: String, port: Int, token: Option[SignedToken]) extends RemoteExecutionClient {

  def sendCommand(logger: Logger, executionCommand: ExecutionCommand, pDest: String, pBody: String): Future[Response] = {
    logger.logInfo("JsRemoteExecutionClient: sent request and waiting for a response")
    val encoded: Future[(dom.BodyInit, Boolean)] = Future.unit.flatMap { _ =>
      if (pBody.length >= 1024 && js.typeOf(js.Dynamic.global.CompressionStream) != "undefined") {
        val source = new dom.Blob(js.Array(pBody)).asInstanceOf[js.Dynamic].stream()
        val compressed = source.pipeThrough(js.Dynamic.newInstance(js.Dynamic.global.CompressionStream)("gzip"))
        new dom.Response(compressed.asInstanceOf[dom.BodyInit]).arrayBuffer().toFuture
          .map(bytes => (bytes.asInstanceOf[dom.BodyInit], true))
      } else Future.successful((pBody, false))
    }

    encoded.recoverWith { case NonFatal(_) =>
      Future.failed(SerializedException(s"Could not encode backend request for command ${executionCommand.name}; browser request compression failed."))
    }.flatMap { (payload, gzip) =>
      val requestHeaders = List(
        "Content-Type" -> "application/json",
        SignedToken.cookieKey -> token.map(_.toJson).getOrElse("")
      ) ++ Option.when(gzip)("Content-Encoding" -> "gzip")
      Future.unit.flatMap { _ => dom.fetch(pDest, new dom.RequestInit {
        method = dom.HttpMethod.POST
        headers = js.Dictionary(requestHeaders*).asInstanceOf[dom.HeadersInit]
        body = payload
        credentials = RequestCredentials.include
        signal = js.Dynamic.global.AbortSignal.timeout(JsRemoteExecutionClient.requestTimeoutMillis).asInstanceOf[dom.AbortSignal]
      }).toFuture }.recoverWith { case NonFatal(error) =>
        val name = error match {
          case jsError: js.JavaScriptException =>
            scala.util.Try(jsError.exception.asInstanceOf[js.Dynamic].selectDynamic("name").asInstanceOf[String]).getOrElse("")
          case _ => ""
        }
        val reason = name match {
          case "TimeoutError" => "The backend did not respond within 60 seconds; it may be stalled or unavailable."
          case "AbortError" => "The browser canceled the request before it completed."
          case _ => "The backend could not be reached; check network access, the backend URL and browser CORS errors."
        }
        // Fetch errors can contain URLs or request details; do not echo them.
        Future.failed(SerializedException(s"Backend request for command ${executionCommand.name} failed: $reason"))
      }
    }
  }


  def handleResponse(logger: Logger, response: Response): Future[ExecutionClientResponse] = {
    logger.logInfo(s"JsRemoteExecutionClient: received a response (code ${response.status})!")
    response.text().toFuture.recoverWith { case NonFatal(_) =>
      Future.failed(SerializedException(s"Could not read backend response body (HTTP ${response.status}); the connection closed or the response was interrupted."))
    }.flatMap { text =>
      if (!response.ok) {
        val error = scala.util.Try(handleText(logger, text)).toOption.flatMap(_.result.left.toOption)
        val detail = error.map(_.msg).getOrElse(scala.util.Try(ujson.read(text)("error").str).getOrElse(response.statusText))
        Future.failed(SerializedException(s"Server responded with status ${response.status}: $detail"))
      } else Future.fromTry(scala.util.Try(handleText(logger, text)))
    }
  }

  def handleText(logger: Logger, text: String): ExecutionClientResponse =
    try read(text)(using ExecutionClientResponse.derived$ReadWriter)
    catch case NonFatal(_) =>
      // Successful login responses can contain credentials; never echo the body or raw parser error.
      throw SerializedException("Could not decode backend execution response: expected the current JSON response format. Check that client and server use the same version and that the proxy returns the backend response.")

  /** The backend refuses login-only commands without a valid token: fail them here, before serializing or sending. */
  override protected def sendTo(logger: Logger, ip: String, port: Int, executionCommand: ExecutionCommand): Future[ExecutionClientResponse] =
    if !executionCommand.requiresLogin then send(logger, executionCommand)
    else token match {
      case Some(signed) if signed.info.expiresAt.isAfter(UtcTime.now()) => send(logger, executionCommand)
      case Some(signed) => Future.failed(SerializedException(
        s"Command ${executionCommand.name} requires a signed-in account, but the session expired at ${signed.info.expiresAt}; it was not sent."))
      case None => Future.failed(SerializedException(
        s"Command ${executionCommand.name} requires a signed-in account; it was not sent."))
    }

  private def send(logger: Logger, executionCommand: ExecutionCommand): Future[ExecutionClientResponse] = {
    val body = write(executionCommand)
    val dest = if (hostname.startsWith("http")) s"$hostname:$port/executeCommand" else s"https://$hostname:$port/executeCommand"

    sendCommand(logger, executionCommand, dest, body).flatMap(response => handleResponse(logger, response))
  }

}

private object JsRemoteExecutionClient {
  /** Queued sync operations wait for each request, so an unresponsive backend must fail in bounded time. */
  val requestTimeoutMillis: Int = 60000
}
