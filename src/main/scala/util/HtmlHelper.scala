package util

import org.scalajs.dom
import org.scalajs.dom.{Blob, URL}

import scala.scalajs.js

object HtmlHelper {

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
