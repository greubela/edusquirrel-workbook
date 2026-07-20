package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.logging.Logger
import org.scalajs.dom
import upickle.default.read

import scala.concurrent.Future
import scala.scalajs.js.JSON

private case class JsRemoteExecutionClient(hostname: String, port: Int) extends RemoteExecutionClient {

  override protected def sendTo(logger: Logger, ip: String, port: Int, executionCommand: ExecutionCommand): Future[Map[String, String]] = {
    val commandJson = executionCommand.toJson
    val dest = if (hostname.startsWith("http")) {
      s"$hostname:$port/executeCommand"
    } else {
      s"https://$hostname:$port/executeCommand"
    }

    dom.fetch(
      dest,
      new dom.RequestInit {
        method = dom.HttpMethod.POST
        headers = JSON.parse("""{"Content-Type":"application/json"}""").asInstanceOf[dom.HeadersInit]
        body = commandJson
      }
    ).toFuture.flatMap { response =>
      logger.logInfo("JsRemoteExecutionClient: sent request and waiting for a response")
      response.text().toFuture.map { body =>
        if (!response.ok) {
          val message = scala.util.Try(read[Map[String, String]](body).getOrElse("error", body)).getOrElse(body)
          val msg = s"Server responded with status ${response.status}: $message"
          logger.logError(msg)
          throw new RuntimeException(msg)
        }
        try {
          read[Map[String, String]](body)
        } catch case e: Throwable => {
          logger.logException(e)
          throw e
        }
      }
    }
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
