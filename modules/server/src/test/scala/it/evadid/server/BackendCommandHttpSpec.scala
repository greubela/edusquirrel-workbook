package it.evadid.server

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.formats.ExecutionClientResponse
import munit.FunSuite
import play.api.Mode
import play.core.server.ServerConfig
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.GZIPOutputStream
import scala.util.Using
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import upickle.default.*

class BackendCommandHttpSpec extends FunSuite {
  private def withServer(run: (HttpClient, String) => Unit): Unit = {
    val server = BackendServer.createServer(ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test))(BackendServer.buildApiRouter)
    val client = HttpClient.newHttpClient()
    try run(client, s"http://127.0.0.1:${server.httpPort.get}")
    finally {
      client.close()
      server.stop()
    }
  }

  private def post(client: HttpClient, base: String, body: String): HttpResponse[String] =
    client.send(HttpRequest.newBuilder(URI.create(base + "/executeCommand"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())

  private def gzip(body: String): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    Using.resource(new GZIPOutputStream(output))(_.write(body.getBytes(UTF_8)))
    output.toByteArray
  }

  private def postEncoded(client: HttpClient, base: String, body: Array[Byte], encoding: String): HttpResponse[String] =
    client.send(HttpRequest.newBuilder(URI.create(base + "/executeCommand"))
      .timeout(java.time.Duration.ofSeconds(20))
      .header("Content-Type", "application/json").header("Content-Encoding", encoding)
      .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString())

  test("gzip commands preserve large Unicode payloads at the execution boundary") {
    withServer { (client, base) =>
      val command = ExecutionCommand("requires-login", ujson.Obj("data" -> ("Grüße 学校 🐿️\n" * 20000)))
      val json = write(command)
      val compressed = gzip(json)
      assert(compressed.length < json.getBytes(UTF_8).length / 10)
      val response = postEncoded(client, base, compressed, "gzip")
      assertEquals(response.statusCode(), 200)
      assertEquals(read[ExecutionClientResponse](response.body()).receivedExecutionCommand, Some(command))
    }
  }

  test("the decoded size limit also applies to highly compressible requests") {
    withServer { (client, base) =>
      val compressed = gzip("x" * (BackendServer.maxCommandBodyBytes.toInt + 1))
      assert(compressed.length < 100000)
      val response = postEncoded(client, base, compressed, "gzip")
      assertEquals(response.statusCode(), 413)
      assert(read[ExecutionClientResponse](response.body()).result.left.toOption.get.msg.contains("byte request limit"))
    }
  }

  test("commands larger than Play's default buffer reach execution instead of returning HTML 413") {
    withServer { (client, base) =>
      val command = ExecutionCommand("requires-login", ujson.Obj("data" -> ("x" * 350000)))
      val response = post(client, base, write(command))
      assertEquals(response.statusCode(), 200)
      val envelope = read[ExecutionClientResponse](response.body())
      assertEquals(envelope.receivedExecutionCommand, Some(command))
      assert(envelope.result.left.toOption.get.msg.contains("requires an account"))
    }
  }

  test("synchronous command failures and invalid JSON return the execution error envelope") {
    withServer { (client, base) =>
      val command = ExecutionCommand("", ujson.Obj())
      val response = post(client, base, write(command))
      assertEquals(response.statusCode(), 200)
      val envelope = read[ExecutionClientResponse](response.body())
      assertEquals(envelope.receivedExecutionCommand, Some(command))
      assert(envelope.result.left.toOption.get.msg.contains("must not be empty"))
      val malformed = post(client, base, "{invalid JSON")
      assertEquals(malformed.statusCode(), 200)
      assert(read[ExecutionClientResponse](malformed.body()).result.isLeft)
    }
  }

  test("oversized commands return a JSON failure and the server keeps serving requests") {
    withServer { (client, base) =>
      val response = post(client, base, "x" * (BackendServer.maxCommandBodyBytes.toInt + 1))
      assertEquals(response.statusCode(), 413)
      assert(response.headers().firstValue("Content-Type").get().startsWith("application/json"))
      val envelope = read[ExecutionClientResponse](response.body())
      assert(envelope.result.left.toOption.get.msg.contains("byte request limit"))
      val health = client.send(HttpRequest.newBuilder(URI.create(base + "/health")).GET().build(), HttpResponse.BodyHandlers.ofString())
      assertEquals(health.statusCode(), 200)
      assertEquals(ujson.read(health.body())("status").str, "ok")
    }
  }
}
