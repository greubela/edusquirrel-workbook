package it.evadid.distribution.clients

import java.time.Instant


import it.evadid.distribution.command.ExecutionCommand
import it.evadid.util.logging.BasicLogger
import munit.FunSuite
import org.scalajs.dom.Response
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

class JsRemoteExecutionClientSpec extends FunSuite {
  private def localClient = JsRemoteExecutionClient("localhost", 443, None)

  private def send(name: String, body: String): Future[Response] =
    localClient.sendCommand(BasicLogger(), ExecutionCommand(name, ujson.Obj()), "https://localhost/executeCommand", body)

  /** Replaces the browser's fetch while `body` runs and restores it afterwards. */
  private def withFetch[A](fetch: js.Function2[String, dom.RequestInit, js.Promise[dom.Response]])(body: => Future[A]): Future[A] = {
    val originalFetch = js.Dynamic.global.fetch
    js.Dynamic.global.fetch = fetch
    Future.unit.flatMap(_ => body).andThen { case _ => js.Dynamic.global.fetch = originalFetch }
  }

  /** Replaces the browser's CompressionStream while `body` runs and restores it afterwards. */
  private def withCompressionStream[A](stream: js.Any)(body: => Future[A]): Future[A] = {
    val originalCompression = js.Dynamic.global.CompressionStream
    js.Dynamic.global.CompressionStream = stream
    Future.unit.flatMap(_ => body).andThen { case _ => js.Dynamic.global.CompressionStream = originalCompression }
  }

  private def inspectSentBody(body: String)(inspect: dom.RequestInit => Future[Unit]): Future[Unit] = {
    val captured = Promise[dom.RequestInit]()
    withFetch((url: String, init: dom.RequestInit) => {
      captured.success(init)
      js.Promise.resolve[dom.Response](new dom.Response("{}"))
    }) {
      send("test", body).flatMap(_ => captured.future).flatMap(inspect)
    }
  }

  /** A fetch response whose body is read lazily from `text`. */
  private def response(ok: Boolean, status: Int, statusText: String)(text: => js.Promise[String]): Response =
    js.Dynamic.literal(ok = ok, status = status, statusText = statusText,
      text = (() => text): js.Function0[js.Promise[String]]).asInstanceOf[Response]

  test("large requests use native gzip and preserve Unicode JSON exactly") {
    val body = ujson.write(ujson.Obj("history" -> ("Grüße 学校 🐿️\n" * 20000)))
    inspectSentBody(body) { init =>
      val headers = new dom.Headers(init.headers.get)
      assertEquals(headers.get("Content-Encoding"), "gzip")
      assertEquals(headers.get("Content-Type"), "application/json")
      val compressed = init.body.asInstanceOf[js.typedarray.ArrayBuffer]
      assert(compressed.byteLength < body.length / 10)
      val stream = new dom.Blob(js.Array(compressed)).asInstanceOf[js.Dynamic].stream()
        .pipeThrough(js.Dynamic.newInstance(js.Dynamic.global.DecompressionStream)("gzip"))
      new dom.Response(stream.asInstanceOf[dom.BodyInit]).text().toFuture.map { restored =>
        assertEquals(restored, body)
      }
    }
  }

  test("requests carry a timeout signal so an unresponsive backend cannot stall the sync queue") {
    inspectSentBody("{}") { init =>
      assert(init.signal.isDefined)
      assert(!init.signal.get.aborted)
      Future.unit
    }
  }

  test("small requests remain plain JSON") {
    val body = "{\"name\":\"health\"}"
    inspectSentBody(body) { init =>
      assertEquals(new dom.Headers(init.headers.get).get("Content-Encoding"), null)
      assertEquals(init.body.asInstanceOf[String], body)
      Future.unit
    }
  }

  test("browsers without CompressionStream can still send large JSON requests") {
    val body = ujson.write(ujson.Obj("history" -> ("x" * 5000)))
    withCompressionStream(js.undefined) {
      inspectSentBody(body) { init =>
        assertEquals(new dom.Headers(init.headers.get).get("Content-Encoding"), null)
        assertEquals(init.body.asInstanceOf[String], body)
        Future.unit
      }
    }
  }

  List("TimeoutError" -> "60 seconds", "AbortError" -> "canceled", "TypeError" -> "network access").foreach { (name, expected) =>
    test(s"$name request failures identify the command and suggest an actionable next step") {
      withFetch((_: String, _: dom.RequestInit) =>
        js.Promise.reject(js.Dynamic.newInstance(js.Dynamic.global.DOMException)("private network details", name))) {
        send("auth-mail-request", "{}")
      }.failed.map { error =>
        assert(error.getMessage.contains("auth-mail-request"))
        assert(error.getMessage.contains(expected))
        assert(!error.getMessage.contains("private network details"))
      }
    }
  }

  test("incompatible success responses explain the response contract without echoing credentials") {
    val response = new dom.Response("private response credentials")
    localClient.handleResponse(BasicLogger(), response).failed.map { error =>
      assert(error.getMessage.contains("Could not decode backend execution response"))
      assert(error.getMessage.contains("same version"))
      assert(!error.getMessage.contains("private response credentials"))
    }
  }

  test("HTTP failures retain their status and server reason instead of decoding a success envelope") {
    val failed = response(ok = false, 503, "Unavailable")(js.Promise.resolve[String]("""{"error":"SMTP unavailable"}"""))
    localClient.handleResponse(BasicLogger(), failed).failed.map { error =>
      assert(error.getMessage.contains("503"))
      assert(error.getMessage.contains("SMTP unavailable"))
    }
  }

  test("HTTP errors containing an execution envelope preserve the actionable server reason") {
    val time = java.time.Instant.parse("2026-11-10T12:00:00Z")
    val envelope = it.evadid.distribution.formats.ExecutionClientResponse(
      it.evadid.distribution.formats.ExecutionClientResponse.ExecutionClientResponseTimestamps(time, time, time),
      Left(it.evadid.distribution.command.SerializedException("Execution command exceeds the byte request limit. Reduce the saved history.")),
      None, it.evadid.distribution.formats.ExecutionClientResponse.AdditionalLoggingOutput.empty)
    val failed = response(ok = false, 413, "Payload Too Large")(js.Promise.resolve[String](upickle.default.write(envelope)))
    localClient.handleResponse(BasicLogger(), failed).failed.map { error =>
      assert(error.getMessage.contains("413"))
      assert(error.getMessage.contains("Reduce the saved history"))
    }
  }

  test("non-JSON HTTP failures use the status rather than producing a JSON parsing error") {
    val failed = response(ok = false, 502, "Bad Gateway")(js.Promise.resolve[String]("<html>Unavailable</html>"))
    localClient.handleResponse(BasicLogger(), failed).failed.map { error =>
      assert(error.getMessage.contains("502: Bad Gateway"))
    }
  }

  test("commands are posted to the configured backend address and decode the execution response") {
    val time = java.time.Instant.parse("2026-11-20T09:00:00Z")
    val command = ExecutionCommand("health", ujson.Obj())
    val envelope = it.evadid.distribution.formats.ExecutionClientResponse.forValue(
      it.evadid.distribution.formats.ExecutionClientResponse.ExecutionClientResponseTimestamps(time, time, time), "ok", command)
    val requests = js.Array[(String, dom.RequestInit)]()
    withFetch((url: String, init: dom.RequestInit) => {
      requests.push(url -> init)
      js.Promise.resolve[dom.Response](new dom.Response(upickle.default.write(envelope)))
    }) {
      val logger = BasicLogger()
      JsRemoteExecutionClient("backend.example.org", 8443, None).executeResponse(logger, command)
        .zip(JsRemoteExecutionClient("http://localhost", 8080, None).executeResponse(logger, command))
    }.map { (secure, local) =>
      assertEquals(secure, envelope)
      assertEquals(local, envelope)
      assertEquals(requests.toList.map(_._1), List("https://backend.example.org:8443/executeCommand", "http://localhost:8080/executeCommand"))
      val init = requests(0)._2
      assertEquals(init.method.get, dom.HttpMethod.POST)
      assertEquals(init.credentials.get, dom.RequestCredentials.include)
      assertEquals(upickle.default.read[ExecutionCommand](init.body.asInstanceOf[String]), command)
      assertEquals(new dom.Headers(init.headers.get).get("Authorization"), "", "anonymous requests carry no token")
    }
  }

  test("a failing browser compressor names the command instead of sending a corrupt request") {
    val sent = js.Array[String]()
    withCompressionStream(js.constructorOf[RejectingCompressionStream]) {
      withFetch((url: String, _: dom.RequestInit) => { sent.push(url); js.Promise.resolve[dom.Response](new dom.Response("{}")) }) {
        send("save-history", "x" * 4096)
      }.failed.map { error =>
        assertEquals(error.getMessage, "Could not encode backend request for command save-history; browser request compression failed.")
        assertEquals(sent.length, 0)
      }
    }
  }

  test("unexpected fetch failures report an unreachable backend") {
    withFetch((_: String, _: dom.RequestInit) => throw IllegalStateException("fetch is disabled")) {
      send("health", "{}")
    }.failed.map { error =>
      assertEquals(error.getMessage,
        "Backend request for command health failed: The backend could not be reached; check network access, the backend URL and browser CORS errors.")
    }
  }

  test("interrupted response bodies report the HTTP status") {
    val interrupted = response(ok = true, 200, "OK")(js.Promise.reject("connection reset"))
    localClient.handleResponse(BasicLogger(), interrupted).failed.map { error =>
      assertEquals(error.getMessage,
        "Could not read backend response body (HTTP 200); the connection closed or the response was interrupted.")
    }
  }

  test("login-only commands without a valid token fail locally; other commands are still sent") {
    import it.evadid.core.datastructures.user.{User, UserTokenInfo}
    import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
    import it.evadid.distribution.commandTypes.{SQLCommands, UserCommands}
    import java.time.Instant
    def signed(expiresAt: Instant) = Some(SignedToken(UserTokenInfo(User.createNewUser("Ada", "ada@school.example"),
      Instant.now().minus(java.time.Duration.ofDays(1)), expiresAt), "signature"))
    val requests = scala.collection.mutable.ListBuffer.empty[String]
    def execute(token: Option[SignedToken], name: String) =
      JsRemoteExecutionClient("localhost", 443, token).executeResponse(BasicLogger(), ExecutionCommand(name, ujson.Obj()))
        .transform(result => scala.util.Success(result.failed.toOption.map(_.getMessage)))
    val store = SQLCommands.bulkStoreToDbCommand.name
    withFetch((_: String, init: dom.RequestInit) => {
      requests += init.body.asInstanceOf[String]
      js.Promise.resolve[dom.Response](new dom.Response("{}"))
    }) {
      for {
        withoutToken <- execute(None, store)
        expired <- execute(signed(Instant.now().minus(java.time.Duration.ofMinutes(1))), SQLCommands.fetchEventsFromDbCommand.name)
        _ = assertEquals(requests.toList, Nil, "nothing is sent that the backend would refuse")
        _ <- execute(None, UserCommands.loginCommand.name)
        _ <- execute(signed(Instant.now().plus(java.time.Duration.ofDays(1))), store)
      } yield {
        assert(withoutToken.exists(_.contains("requires a signed-in account")), withoutToken)
        assert(expired.exists(_.contains("expired")), expired)
        assertEquals(requests.size, 2, "public commands and commands with a valid token are sent")
      }
    }
  }

  test("a deferred backend waits for its configuration, then posts to the configured origin") {
    val config = Promise[RemoteExecutionConfig]()
    val requested = Promise[String]()
    withFetch((url: String, _: dom.RequestInit) => {
      requested.trySuccess(url)
      js.Promise.resolve[dom.Response](new dom.Response("{}"))
    }) {
      val response = RemoteExecutionConfig.deferredExecutor(config.future, None)
        .executeResponse(BasicLogger(), ExecutionCommand("deferred", ujson.Obj())).transform(outcome => scala.util.Success(outcome))
      Future.unit.flatMap { _ =>
        assert(!requested.isCompleted, "nothing is sent before the configuration is known")
        config.success(RemoteExecutionConfig.fromUrl("http://localhost:9000"))
        requested.future
      }.map(url => assertEquals(url, "http://localhost:9000/executeCommand")).flatMap(_ => response.map(_ => ()))
    }
  }

  test("a failed backend configuration fails each command with its reason") {
    RemoteExecutionConfig.deferredExecutor(Future.failed(IllegalArgumentException("invalid backend URL")), None)
      .executeResponse(BasicLogger(), ExecutionCommand("deferred", ujson.Obj())).transform(outcome => scala.util.Success(outcome))
      .map(outcome => assert(outcome.failed.toOption.exists(_.getMessage.contains("invalid backend URL")), outcome.toString))
  }
}

/** A browser compression stream that cannot be created, e.g. when the encoder is unavailable. */
private class RejectingCompressionStream(format: String) extends js.Object {
  throw js.JavaScriptException(js.Error(s"$format compression is unavailable"))


}
