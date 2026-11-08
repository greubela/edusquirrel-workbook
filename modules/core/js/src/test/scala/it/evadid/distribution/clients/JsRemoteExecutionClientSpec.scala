package it.evadid.distribution.clients

import it.evadid.util.logging.BasicLogger
import munit.FunSuite
import org.scalajs.dom.Response
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

class JsRemoteExecutionClientSpec extends FunSuite {
  test("HTTP failures retain their status and server reason instead of decoding a success envelope") {
    val response = js.Dynamic.literal(ok = false, status = 503, statusText = "Unavailable",
      text = (() => js.Promise.resolve("""{"error":"SMTP unavailable"}""")): js.Function0[js.Promise[String]])
    JsRemoteExecutionClient("localhost", 443, None).handleResponse(BasicLogger(), response.asInstanceOf[Response]).failed.map { error =>
      assert(error.getMessage.contains("503"))
      assert(error.getMessage.contains("SMTP unavailable"))
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
