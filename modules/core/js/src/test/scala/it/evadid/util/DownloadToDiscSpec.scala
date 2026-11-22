package it.evadid.util

import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

class DownloadToDiscSpec extends JsDomSuite {
  // Node's Blob and URL.createObjectURL model the browser's object URL store.
  override protected def domGlobals: List[String] = List("window", "document", "Element", "Node", "Event", "MouseEvent")

  private val buffer = js.Dynamic.global.require("node:buffer")

  private case class Download(name: String, href: String, hidden: Boolean, attached: Boolean, content: Future[String], mime: String)

  /** Captures the hidden anchor click and reads the object URL before the facade revokes it. */
  private def capture(action: => Unit): Download = {
    val downloads = js.Array[Download]()
    val listener: js.Function1[dom.Event, Unit] = event => {
      event.preventDefault()
      val anchor = event.target.asInstanceOf[dom.html.Anchor]
      val blob: js.Dynamic = if anchor.href.startsWith("blob:") then buffer.resolveObjectURL(anchor.href) else js.undefined.asInstanceOf[js.Dynamic]
      downloads.push(Download(anchor.download, anchor.href, anchor.style.display == "none", anchor.isConnected,
        if js.isUndefined(blob) then Future.successful("") else blob.text().asInstanceOf[js.Promise[String]].toFuture,
        if js.isUndefined(blob) then "" else blob.selectDynamic("type").asInstanceOf[String]))
    }
    dom.document.addEventListener("click", listener)
    try action finally dom.document.removeEventListener("click", listener)
    assertEquals(downloads.length, 1)
    assertEquals(dom.document.querySelectorAll("a").length, 0, "the temporary anchor must be removed")
    downloads(0)
  }

  private def revoked(download: Download): Boolean = js.isUndefined(buffer.resolveObjectURL(download.href))

  test("text downloads use a hidden anchor with the desired name and revoke their object URL") {
    val download = capture(DownloadToDisc.instance.downloadFile("notes.txt", "Grüße 🐿"))
    assertEquals(download.name, "notes.txt")
    assert(download.hidden && download.attached)
    assertEquals(download.mime, "text/plain;charset=utf-8")
    assert(revoked(download))
    download.content.map(text => assertEquals(text, "Grüße 🐿"))
  }

  test("SVG downloads keep the SVG media type") {
    val svg = "<svg xmlns='http://www.w3.org/2000/svg'/>"
    val download = capture(DownloadToDisc.instance.downloadSvg("chart.svg", svg))
    assertEquals(download.mime, "image/svg+xml;charset=utf-8")
    download.content.map(text => assertEquals(text, svg))
  }

  test("binary downloads preserve every byte value") {
    val bytes = Array[Byte](0, 65, 127, -128, -1)
    val download = capture(DownloadToDisc.instance.downloadFile("data.bin", bytes))
    assertEquals(download.mime, "application/octet-stream")
    val blob = js.Dynamic.newInstance(js.Dynamic.global.Blob)(js.Array(new Uint8Array(js.Array[Short](0, 65, 127, 128, 255)).buffer))
    download.content.zip(blob.text().asInstanceOf[js.Promise[String]].toFuture).map((actual, expected) => assertEquals(actual, expected))
  }

  test("URL downloads link to the resource directly") {
    val download = capture(DownloadToDisc.instance.downloadFromUrl("logo.png", new dom.URL("https://example.org/logo.png")))
    assertEquals(download.href, "https://example.org/logo.png")
    assertEquals(download.name, "logo.png")
  }

  test("the object URL is revoked even when the page rejects the download anchor") {
    val urls = js.Array[String]()
    val body = dom.document.body.asInstanceOf[js.Dynamic]
    body.appendChild = ((node: dom.Node) => {
      urls.push(node.asInstanceOf[dom.html.Anchor].href)
      throw js.JavaScriptException("blocked")
    }): js.Function1[dom.Node, Unit]
    try intercept[js.JavaScriptException](DownloadToDisc.instance.downloadFile("x.txt", "x"))
    finally js.special.delete(body, "appendChild")
    assertEquals(urls.length, 1)
    assert(urls(0).startsWith("blob:"))
    assert(js.isUndefined(buffer.resolveObjectURL(urls(0))))
  }
}
