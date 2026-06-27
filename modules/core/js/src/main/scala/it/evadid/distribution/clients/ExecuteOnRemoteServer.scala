package it.evadid.distribution.clients

import it.evadid.core.datastructures.state.async.{AsyncData, AsyncFuture}
import it.evadid.core.util.io.serializer.DefaultSerializer
import org.scalajs.dom
import upickle.default.read
import it.evadid.distribution.command.*

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.JSON
import scala.scalajs.js.Thenable.Implicits.*
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoUntyped
import it.evadid.util.Logger

case class ExecuteOnRemoteServer(hostname: String, port: Int) extends ExecutionClient {


  override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = true

  override def handleExecution(executionCommand: ExecutionCommand, logger: Logger): AsyncData[Nothing, ExecutionInfo] = {
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
    AsyncData.forFuture(future)
  }
}
