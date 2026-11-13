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
}
