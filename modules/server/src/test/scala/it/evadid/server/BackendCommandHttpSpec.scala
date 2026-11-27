package it.evadid.server

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.formats.ExecutionClientResponse
import munit.{AnyFixture, FunSuite}
import play.api.Mode
import play.core.server.{Server, ServerConfig}
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.GZIPOutputStream
import scala.util.Using
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import upickle.default.*

class BackendCommandHttpSpec extends FunSuite {
  private case class Backend(server: Server, client: HttpClient) {
    val base = s"http://127.0.0.1:${server.httpPort.get}"
  }

  /** The API router keeps no state between requests, so one server serves the whole suite. */
  private val backend = new Fixture[Backend]("backend server") {
    private var running: Option[Backend] = None
    def apply(): Backend = running.get
    override def beforeAll(): Unit = running = Some(Backend(
      BackendServer.createServer(ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test))(BackendServer.buildApiRouter),
      HttpClient.newHttpClient()))
    override def afterAll(): Unit = running.foreach { started =>
      started.client.close()
      started.server.stop()
    }
  }
  override def munitFixtures: Seq[AnyFixture[?]] = List(backend)

  private def send(request: HttpRequest.Builder): HttpResponse[String] =
    backend().client.send(request.build(), HttpResponse.BodyHandlers.ofString())

  private def commandRequest = HttpRequest.newBuilder(URI.create(backend().base + "/executeCommand"))
    .header("Content-Type", "application/json")

  private def post(body: String): HttpResponse[String] =
    send(commandRequest.POST(HttpRequest.BodyPublishers.ofString(body)))

  private def postEncoded(body: Array[Byte], encoding: String): HttpResponse[String] =
    send(commandRequest.timeout(java.time.Duration.ofSeconds(20)).header("Content-Encoding", encoding)
      .POST(HttpRequest.BodyPublishers.ofByteArray(body)))

  private def gzip(body: String): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    Using.resource(new GZIPOutputStream(output))(_.write(body.getBytes(UTF_8)))
    output.toByteArray
  }

  private def failureMessage(response: HttpResponse[String]): String =
    read[ExecutionClientResponse](response.body()).result.left.toOption.get.msg

  test("gzip commands preserve large Unicode payloads at the execution boundary") {
    val command = ExecutionCommand("requires-login", ujson.Obj("data" -> ("Grüße 学校 🐿️\n" * 20000)))
    val json = write(command)
    val compressed = gzip(json)
    assert(compressed.length < json.getBytes(UTF_8).length / 10)
    val response = postEncoded(compressed, "gzip")
    assertEquals(response.statusCode(), 200)
    assertEquals(read[ExecutionClientResponse](response.body()).receivedExecutionCommand, Some(command))
  }

  test("the decoded size limit also applies to highly compressible requests") {
    val compressed = gzip("x" * (BackendServer.maxCommandBodyBytes.toInt + 1))
    assert(compressed.length < 100000)
    val response = postEncoded(compressed, "gzip")
    assertEquals(response.statusCode(), 413)
    assert(failureMessage(response).contains("byte request limit"))
  }

  test("commands larger than Play's default buffer reach execution instead of returning HTML 413") {
    val command = ExecutionCommand("requires-login", ujson.Obj("data" -> ("x" * 350000)))
    val response = post(write(command))
    assertEquals(response.statusCode(), 200)
    assertEquals(read[ExecutionClientResponse](response.body()).receivedExecutionCommand, Some(command))
    assert(failureMessage(response).contains("requires an account"))
  }

  test("synchronous command failures and invalid JSON return the execution error envelope") {
    val command = ExecutionCommand("", ujson.Obj())
    val response = post(write(command))
    assertEquals(response.statusCode(), 200)
    assertEquals(read[ExecutionClientResponse](response.body()).receivedExecutionCommand, Some(command))
    assert(failureMessage(response).contains("must not be empty"))
    val malformed = post("{invalid JSON")
    assertEquals(malformed.statusCode(), 200)
    assert(read[ExecutionClientResponse](malformed.body()).result.isLeft)
  }

  test("oversized commands return a JSON failure and the server keeps serving requests") {
    val response = post("x" * (BackendServer.maxCommandBodyBytes.toInt + 1))
    assertEquals(response.statusCode(), 413)
    assert(response.headers().firstValue("Content-Type").get().startsWith("application/json"))
    assert(failureMessage(response).contains("byte request limit"))
    val health = send(HttpRequest.newBuilder(URI.create(backend().base + "/health")).GET())
    assertEquals(health.statusCode(), 200)
    assertEquals(ujson.read(health.body())("status").str, "ok")
  }
}
