package util.web

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.{Blob, File, URL}
import util.TypeConversion

import java.io.IOException
import scala.concurrent.{ExecutionContext, Future, Promise}
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
    val promise = Promise[Array[Byte]]()
    dom.fetch(url).`then`(
      onRejected = err => promise.failure(new IOException(s"Unknown Error while fetching '$url': ${err.toString}")),
      onFulfilled = response =>
        if (!response.ok)
          promise.failure(new IOException(s"IO Error while fetching '$url': response status ${response.status}"))
        else response.arrayBuffer().`then`(
          onRejected = err => promise.failure(new IOException(s"Error loading buffer after fetching '$url': ${err.toString}")),
          onFulfilled = buffer => promise.success(TypeConversion.decodeArrayBuffer(buffer)))
    )
    promise.future
  }


  def downloadFromUrl(desiredName: String, url: URL): Unit = {
    val anchor = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    anchor.href = url.toString
    anchor.download = desiredName
    anchor.style.display = "none"

    dom.document.body.appendChild(anchor)
    anchor.click()
    dom.document.body.removeChild(anchor)
  }

  def downloadFile(desiredFilename: String, uint8: Uint8Array): Unit = {
    val blob = new dom.Blob(
      js.Array(uint8.buffer),
      new dom.BlobPropertyBag {
        `type` = "application/octet-stream"
      }
    )

    val url = URL.createObjectURL(blob)

    val a = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    a.href = url
    a.download = desiredFilename
    a.style.display = "none"

    dom.document.body.appendChild(a)
    a.click()

    dom.document.body.removeChild(a)
    URL.revokeObjectURL(url)
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

    val a = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    a.href = url
    a.download = desiredFilename
    a.style.display = "none"

    dom.document.body.appendChild(a)
    a.click()

    dom.document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

}
