package it.evadid.distribution.clients

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import it.evadid.distribution.ExecutionCommand
import munit.FunSuite
import upickle.default.{read, write}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Success

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
        val command = read[ExecutionCommand](requestBody)

        val response = ExecutionCommand.ExecutionInfo(
          command = command,
          result = Success(ExecutionCommand.ExecutionResult(command.params, "ok", "")),
          meta = None
        )

        val payload = Map("executionInfo" -> write(response))
        val bytes = write(payload).getBytes(StandardCharsets.UTF_8)
        exchange.getResponseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.length)
        val os = exchange.getResponseBody
        os.write(bytes)
        os.close()
    } { port =>
      val client = ServerExecution("127.0.0.1", port)
      val command = ExecutionCommand("echo", Map("x" -> "1"))

      val info = Await.result(client.executeCommand(command), 5.seconds)

      assertEquals(info.command, command)
      assertEquals(info.result.map(_.stdOut).get, "ok")
      assertEquals(info.result.map(_.data).get, Map("x" -> "1"))
    }
  }

  test("ServerExecution raises error for non-200 response") {
    withServer { exchange =>
      val bytes = write(Map("error" -> "command failed")).getBytes(StandardCharsets.UTF_8)
      exchange.sendResponseHeaders(500, bytes.length)
      val os = exchange.getResponseBody
      os.write(bytes)
      os.close()
    } { port =>
      val client = ServerExecution("127.0.0.1", port)
      val command = ExecutionCommand("echo", Map.empty)
      intercept[RuntimeException] {
        Await.result(client.executeCommand(command), 5.seconds)
      }
    }
  }

  test("ServerExecution raises error if executionInfo field is missing") {
    withServer { exchange =>
      val bytes = write(Map("status" -> "ok")).getBytes(StandardCharsets.UTF_8)
      exchange.sendResponseHeaders(200, bytes.length)
      val os = exchange.getResponseBody
      os.write(bytes)
      os.close()
    } { port =>
      val client = ServerExecution("127.0.0.1", port)
      val command = ExecutionCommand("echo", Map.empty)
      intercept[IllegalStateException] {
        Await.result(client.executeCommand(command), 5.seconds)
      }
    }
  }
}
