package util.web

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.{Blob, File, URL}
import util.TypeConversion

import java.io.IOException
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}
import scala.util.*

object DownloadHelper {

  def fetchFile(file: File): Future[Array[Byte]] = {
    val reader = new dom.FileReader()
    reader.readAsArrayBuffer(file)

    val promise = Promise[Array[Byte]]()
    reader.onload = _ => promise.success(TypeConversion.decodeArrayBuffer(reader.result.asInstanceOf[ArrayBuffer]))
    reader.onerror = event => promise.failure(new Exception(s"Could not read '${file.name}': ${event.toString}"))
    promise.future
  }

  def fetchUrl(url: String): Future[Array[Byte]] = {
    JsHelpers.promiseToFuture(dom.fetch(url))
      .recoverWith { case err =>
        Future.failed(new IOException(s"Unknown Error while fetching '$url': ${err.toString}"))
      }
      .flatMap { response =>
        if (!response.ok)
          Future.failed(new IOException(s"IO Error while fetching '$url': response status ${response.status}"))
        else
          JsHelpers.promiseToFuture(response.arrayBuffer()).map(TypeConversion.decodeArrayBuffer).recoverWith { case err =>
            Future.failed(new IOException(s"Error loading buffer after fetching '$url': ${err.toString}"))
          }
      }
  }


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

}
