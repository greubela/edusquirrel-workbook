import it.evadid.worker.WebWorkerBackendServer
import munit.FunSuite
import upickle.default.*

class BackendWorkerTest extends FunSuite {
  test("worker message codec preserves escaped and Unicode strings") {
    val payload = Map("requestId" -> "id", "command" -> "line1\n\"Grüße\"")
    assertEquals(read[Map[String, String]](write(payload)(using WebWorkerBackendServer.mapRW))(using WebWorkerBackendServer.mapRW), payload)
  }
  test("worker message codec handles empty payloads") {
    assertEquals(read[Map[String, String]]("{}")(using WebWorkerBackendServer.mapRW), Map.empty[String, String])
  }
  test("worker message codec rejects non-string fields") {
    intercept[Exception](read[Map[String, String]]("{\"requestId\":12}")(using WebWorkerBackendServer.mapRW))
  }
}
