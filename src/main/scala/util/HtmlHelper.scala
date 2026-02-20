package util

import com.raquo.laminar.api.L.*
import contentmanagement.model.image.{FullImage, ImageDescription}
import contentmanagement.storage.ImageStorage
import org.scalajs.dom
import org.scalajs.dom.{Blob, URL}

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.{FullImage, ImageDescription}
import contentmanagement.storage.ImageStorage
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.fileSubmission.TurtleStitchFileFactory
import util.HtmlHelper
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.*
import scala.concurrent.ExecutionContext
import scala.scalajs.js

object HtmlHelper {

  def imagePreview(imgDesc: ImageDescription): Signal[Element] = {

    val fullImageVar = ImageStorage.loadFullImageIntoVar(imgDesc)(ExecutionContext.global)

    def imageLoadingElement(): Element = 
      div(
      cls := "preview-content",
      "Image has not loaded yet"
      )
    

    def imageLoadedElement(img: FullImage): Element =
      div(
        cls := "preview-content",
        L.img(src <-- fullImageVar.signal.map(_.get.imgSourceString), styleAttr := "max-width: 100%; border: 1px solid #ccc;")
      )

    fullImageVar.signal.map {
      case None => imageLoadingElement()
      case Some(img) => imageLoadedElement(img)
    }   
    
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
