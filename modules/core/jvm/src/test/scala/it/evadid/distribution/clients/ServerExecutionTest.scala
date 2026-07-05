package it.evadid.distribution.clients

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import it.evadid.distribution.command.{ExecutionCommand, ExecutionResult}
import it.evadid.util.logging.{BasicLogger, Logger}
import munit.FunSuite
import it.evadid.distribution.formats.ExecutionClientResponse
import upickle.default.write

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ServerExecutionTest extends FunSuite {

  private def withServer(handler: HttpExchange => Unit)(run: Int => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/executeCommand", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = handler(exchange)
    })
    server.start()
    try {
      run(server.getAddress.getPort)
    } finally {
      server.stop(0)
    }
  }

  test("ServerExecution sends command and decodes server response") {
    withServer { exchange =>
      val requestBody = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
      val command = ExecutionCommand.fromJson(requestBody)

      val history = it.evadid.distribution.command.ExecutionHistory(
        java.time.LocalDateTime.parse("2026-01-01T08:00:00"),
        java.time.LocalDateTime.parse("2026-01-01T08:00:01"),
        java.time.LocalDateTime.parse("2026-01-01T08:00:02"),
        java.time.LocalDateTime.parse("2026-01-01T08:00:03")
      )
      val response = ExecutionClientResponse(history.timestampCommandReceived, history.timestampExecutionStarted, history.timestampExecutionFinished, Right(command.params), Some(command), "ok", "")

      val bytes = write(response.serializedToMap()).getBytes(StandardCharsets.UTF_8)
      exchange.getResponseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, bytes.length)
      val os = exchange.getResponseBody
      os.write(bytes)
      os.close()
    } { port =>
      val client = JvmRemoteExecutionClient("127.0.0.1", port)
      val command = ExecutionCommand("echo", Map("x" -> "1"))

      val info = Await.result(client.handleExecution(command, BasicLogger()), 5.seconds)

      assertEquals(info.parsedExecutionCommand, Some(command))
      assertEquals(info.response, Right(Map("x" -> "1")))
      assert(info.loggerOut.contains("ok"))
    }
  }
}
