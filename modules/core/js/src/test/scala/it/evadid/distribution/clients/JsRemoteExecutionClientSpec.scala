package it.evadid.distribution.clients

import it.evadid.util.logging.BasicLogger
import munit.FunSuite
import org.scalajs.dom.Response
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

class JsRemoteExecutionClientSpec extends FunSuite {
  private def inspectSentBody(body: String)(inspect: dom.RequestInit => Future[Unit]): Future[Unit] = {
    val captured = Promise[dom.RequestInit]()
    val originalFetch = js.Dynamic.global.fetch
    js.Dynamic.global.fetch = ((url: String, init: dom.RequestInit) => {
      captured.success(init)
      js.Promise.resolve(new dom.Response("{}"))
    }): js.Function2[String, dom.RequestInit, js.Promise[dom.Response]]
    Future.unit.flatMap { _ =>
      JsRemoteExecutionClient("localhost", 443, None).sendCommand(BasicLogger(),
        it.evadid.distribution.command.ExecutionCommand("test", ujson.Obj()), "https://localhost/executeCommand", body)
    }.flatMap(_ => captured.future).flatMap(inspect)
      .andThen { case _ => js.Dynamic.global.fetch = originalFetch }
  }

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
    val originalCompression = js.Dynamic.global.CompressionStream
    js.Dynamic.global.CompressionStream = js.undefined
    val body = ujson.write(ujson.Obj("history" -> ("x" * 5000)))
    inspectSentBody(body) { init =>
      assertEquals(new dom.Headers(init.headers.get).get("Content-Encoding"), null)
      assertEquals(init.body.asInstanceOf[String], body)
      Future.unit
    }.andThen { case _ => js.Dynamic.global.CompressionStream = originalCompression }
  }

  List("TimeoutError" -> "60 seconds", "AbortError" -> "canceled", "TypeError" -> "network access").foreach { (name, expected) =>
    test(s"$name request failures identify the command and suggest an actionable next step") {
      val originalFetch = js.Dynamic.global.fetch
      js.Dynamic.global.fetch = ((_: String, _: dom.RequestInit) =>
        js.Promise.reject(js.Dynamic.newInstance(js.Dynamic.global.DOMException)("private network details", name))): js.Function2[String, dom.RequestInit, js.Promise[Response]]
      Future.unit.flatMap { _ =>
        JsRemoteExecutionClient("localhost", 443, None).sendCommand(BasicLogger(),
          it.evadid.distribution.command.ExecutionCommand("auth-mail-request", ujson.Obj()),
          "https://localhost/executeCommand", "{}")
      }.failed.map { error =>
        assert(error.getMessage.contains("auth-mail-request"))
        assert(error.getMessage.contains(expected))
        assert(!error.getMessage.contains("private network details"))
      }.andThen { case _ => js.Dynamic.global.fetch = originalFetch }
    }
  }

  test("incompatible success responses explain the response contract without echoing credentials") {
    val response = new dom.Response("private response credentials")
    JsRemoteExecutionClient("localhost", 443, None).handleResponse(BasicLogger(), response).failed.map { error =>
      assert(error.getMessage.contains("Could not decode backend execution response"))
      assert(error.getMessage.contains("same version"))
      assert(!error.getMessage.contains("private response credentials"))
    }
  }

  test("HTTP failures retain their status and server reason instead of decoding a success envelope") {
    val response = js.Dynamic.literal(ok = false, status = 503, statusText = "Unavailable",
      text = (() => js.Promise.resolve("""{"error":"SMTP unavailable"}""")): js.Function0[js.Promise[String]])
    JsRemoteExecutionClient("localhost", 443, None).handleResponse(BasicLogger(), response.asInstanceOf[Response]).failed.map { error =>
      assert(error.getMessage.contains("503"))
      assert(error.getMessage.contains("SMTP unavailable"))
    }
  }

  test("HTTP errors containing an execution envelope preserve the actionable server reason") {
    val time = java.time.LocalDateTime.parse("2026-11-10T12:00:00")
    val envelope = it.evadid.distribution.formats.ExecutionClientResponse(
      it.evadid.distribution.formats.ExecutionClientResponse.ExecutionClientResponseTimestamps(time, time, time),
      Left(it.evadid.distribution.command.SerializedException("Execution command exceeds the byte request limit. Reduce the saved history.")),
      None, it.evadid.distribution.formats.ExecutionClientResponse.AdditionalLoggingOutput.empty)
    val response = js.Dynamic.literal(ok = false, status = 413, statusText = "Payload Too Large",
      text = (() => js.Promise.resolve(upickle.default.write(envelope))): js.Function0[js.Promise[String]])
    JsRemoteExecutionClient("localhost", 443, None).handleResponse(BasicLogger(), response.asInstanceOf[Response]).failed.map { error =>
      assert(error.getMessage.contains("413"))
      assert(error.getMessage.contains("Reduce the saved history"))
    }
  }

  test("non-JSON HTTP failures use the status rather than producing a JSON parsing error") {
    val response = js.Dynamic.literal(ok = false, status = 502, statusText = "Bad Gateway",
      text = (() => js.Promise.resolve("<html>Unavailable</html>")): js.Function0[js.Promise[String]])
    JsRemoteExecutionClient("localhost", 443, None).handleResponse(BasicLogger(), response.asInstanceOf[Response]).failed.map { error =>
      assert(error.getMessage.contains("502: Bad Gateway"))
    }
  }

  private def withFetch[A](fetch: js.Function2[String, dom.RequestInit, js.Promise[dom.Response]])(body: => Future[A]): Future[A] = {
    val originalFetch = js.Dynamic.global.fetch
    js.Dynamic.global.fetch = fetch
    Future.unit.flatMap(_ => body).andThen { case _ => js.Dynamic.global.fetch = originalFetch }
  }

  test("commands are posted to the configured backend address and decode the execution response") {
    val time = java.time.LocalDateTime.parse("2026-11-20T09:00:00")
    val command = it.evadid.distribution.command.ExecutionCommand("health", ujson.Obj())
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
      assertEquals(upickle.default.read[it.evadid.distribution.command.ExecutionCommand](init.body.asInstanceOf[String]), command)
      assertEquals(new dom.Headers(init.headers.get).get("Authorization"), "", "anonymous requests carry no token")
    }
  }

  test("a failing browser compressor names the command instead of sending a corrupt request") {
    val originalCompression = js.Dynamic.global.CompressionStream
    js.Dynamic.global.CompressionStream = js.constructorOf[RejectingCompressionStream]
    val sent = js.Array[String]()
    withFetch((url: String, _: dom.RequestInit) => { sent.push(url); js.Promise.resolve[dom.Response](new dom.Response("{}")) }) {
      JsRemoteExecutionClient("localhost", 443, None).sendCommand(BasicLogger(),
        it.evadid.distribution.command.ExecutionCommand("save-history", ujson.Obj()), "https://localhost/executeCommand", "x" * 4096)
    }.failed.map { error =>
      assertEquals(error.getMessage, "Could not encode backend request for command save-history; browser request compression failed.")
      assertEquals(sent.length, 0)
    }.andThen { case _ => js.Dynamic.global.CompressionStream = originalCompression }
  }

  test("unexpected fetch failures report an unreachable backend") {
    withFetch((_: String, _: dom.RequestInit) => throw IllegalStateException("fetch is disabled")) {
      JsRemoteExecutionClient("localhost", 443, None).sendCommand(BasicLogger(),
        it.evadid.distribution.command.ExecutionCommand("health", ujson.Obj()), "https://localhost/executeCommand", "{}")
    }.failed.map { error =>
      assertEquals(error.getMessage,
        "Backend request for command health failed: The backend could not be reached; check network access, the backend URL and browser CORS errors.")
    }
  }

  test("interrupted response bodies report the HTTP status") {
    val response = js.Dynamic.literal(ok = true, status = 200, statusText = "OK",
      text = (() => js.Promise.reject("connection reset")): js.Function0[js.Promise[String]])
    JsRemoteExecutionClient("localhost", 443, None).handleResponse(BasicLogger(), response.asInstanceOf[Response]).failed.map { error =>
      assertEquals(error.getMessage,
        "Could not read backend response body (HTTP 200); the connection closed or the response was interrupted.")
    }
  }

  test("login-only commands without a valid token fail locally; other commands are still sent") {
    import it.evadid.core.datastructures.user.{User, UserTokenInfo}
    import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
    import it.evadid.distribution.command.ExecutionCommand
    import it.evadid.distribution.commandTypes.{SQLCommands, UserCommands}
    import java.time.LocalDateTime
    def signed(expiresAt: LocalDateTime) = Some(SignedToken(UserTokenInfo(User.createNewUser("Ada", "ada@school.example"),
      LocalDateTime.now().minusDays(1), expiresAt), "signature"))
    val requests = scala.collection.mutable.ListBuffer.empty[String]
    val originalFetch = js.Dynamic.global.fetch
    js.Dynamic.global.fetch = ((url: String, init: dom.RequestInit) => {
      requests += init.body.asInstanceOf[String]
      js.Promise.resolve(new dom.Response("{}"))
    }): js.Function2[String, dom.RequestInit, js.Promise[dom.Response]]
    def execute(token: Option[SignedToken], name: String) =
      JsRemoteExecutionClient("localhost", 443, token).executeResponse(BasicLogger(), ExecutionCommand(name, ujson.Obj()))
        .transform(result => scala.util.Success(result.failed.toOption.map(_.getMessage)))
    val store = SQLCommands.bulkStoreToDbCommand.name
    (for {
      withoutToken <- execute(None, store)
      expired <- execute(signed(LocalDateTime.now().minusMinutes(1)), SQLCommands.fetchEventsFromDbCommand.name)
      _ = assertEquals(requests.toList, Nil, "nothing is sent that the backend would refuse")
      _ <- execute(None, UserCommands.loginCommand.name)
      _ <- execute(signed(LocalDateTime.now().plusDays(1)), store)
    } yield {
      assert(withoutToken.exists(_.contains("requires a signed-in account")), withoutToken)
      assert(expired.exists(_.contains("expired")), expired)
      assertEquals(requests.size, 2, "public commands and commands with a valid token are sent")
    }).andThen { case _ => js.Dynamic.global.fetch = originalFetch }
  }
}

/** A browser compression stream that cannot be created, e.g. when the encoder is unavailable. */
private class RejectingCompressionStream(format: String) extends js.Object {
  throw js.JavaScriptException(js.Error(s"$format compression is unavailable"))

}
