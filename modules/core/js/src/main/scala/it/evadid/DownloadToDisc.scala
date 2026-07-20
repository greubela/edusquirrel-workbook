package it.evadid

import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import org.scalajs.dom
import org.scalajs.dom.{Blob, URL}

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array


object DownloadToDisc {
  val instance = DownloadToDisc(Logger.withNameAndPrefixes(Some("DownloadToDiskSingletonLogger"), PrintToStdLogger.printWarnAndError))
}


case class DownloadToDisc(logger: Logger) {

  private def triggerDownload(url: String, filename: String): Unit = {
    val anchor = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    anchor.href = url
    anchor.download = filename
    anchor.style.display = "none"

    dom.document.body.appendChild(anchor)
    anchor.click()
    dom.document.body.removeChild(anchor)
  }

  private def downloadFromObjectUrl(desiredFilename: String, objectUrl: String): Unit =
    try triggerDownload(objectUrl, desiredFilename)
    finally URL.revokeObjectURL(objectUrl)

  def downloadFromUrl(desiredName: String, url: URL): Unit = {
    triggerDownload(url.toString, desiredName)
  }

  def downloadFile(desiredFilename: String, uint8: Uint8Array): Unit = {
    val blob = new dom.Blob(
      js.Array(uint8.buffer),
      new dom.BlobPropertyBag {
        `type` = "application/octet-stream"
      }
    )

    val url = URL.createObjectURL(blob)
    downloadFromObjectUrl(desiredFilename, url)
  }

  def downloadFile(desiredFilename: String, bytes: Array[Byte]): Unit = {
    val uint8 = new Uint8Array(bytes.length)
    var i = 0
    while (i < bytes.length) {
      uint8(i) = ((bytes(i) & 0xff).toShort)
      i += 1
    }
    downloadFile(desiredFilename, uint8)
  }

  def downloadFile(desiredFilename: String, content: String): Unit = {
    val blob = new Blob(
      js.Array(content),
      new dom.BlobPropertyBag {
        `type` = "text/plain;charset=utf-8"
      }
    )

    val url = URL.createObjectURL(blob)
    downloadFromObjectUrl(desiredFilename, url)
  }

  def downloadSvg(desiredFilename: String, svgContent: String): Unit = {
    val blob = new Blob(
      js.Array(svgContent),
      new dom.BlobPropertyBag {
        `type` = "image/svg+xml;charset=utf-8"
      }
    )

    val url = URL.createObjectURL(blob)
    downloadFromObjectUrl(desiredFilename, url)
  }
}
