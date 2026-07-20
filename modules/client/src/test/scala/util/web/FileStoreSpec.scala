package util.web

import munit.FunSuite
import org.scalajs.dom

import scala.util.Try

class FileStoreSpec extends FunSuite {

  private def hasDomRuntime: Boolean =
    Try(dom.document).isSuccess && Try(dom.URL).isSuccess

  test("downloadFile(content) smoke test") {
    if (hasDomRuntime) {
      HomepageFileStore.downloadFile("smoke.txt", "hello")
    }
    assert(true)
  }

  test("downloadFromUrl smoke test") {
    if (hasDomRuntime) {
      HomepageFileStore.downloadFromUrl("source.txt", new dom.URL("https://example.com/source.txt"))
    }
    assert(true)
  }
}
