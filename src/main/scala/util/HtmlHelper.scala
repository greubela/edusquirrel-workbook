package util

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.{FullImage, ImageDescription}
import contentmanagement.storage.ImageStorage
import org.scalajs.dom
import org.scalajs.dom.{Blob, URL}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.util.*

object HtmlHelper {
    
  def toImgFromDataSrc(classStr: String, v: Signal[Option[String]]): Element =
    div(
      cls := classStr,
      child <-- v.map {

        case None =>
          println("nope")
          div("Image has not loaded yet")

        case Some(rawSrc) =>
          println("calculating in child, curr val: " + rawSrc.take(30) + ", len=" + rawSrc.length)
          // sanitize hard (cheap + removes line breaks)
          val srcClean = rawSrc.replaceAll("\\s", "")

          img(
            src := srcClean,
            styleAttr := "max-width: 100%; border: 1px solid #ccc;",
            onLoad --> (_ => println("IMG LOADED, prefix=" + srcClean.take(30) + ", len=" + srcClean.length)),
            onError --> (_ => println("IMG ERROR, prefix=" + srcClean.take(30) + ", len=" + srcClean.length))
          )
      }
    )

  def toElementSignalDataSrc(classStr: String, varOpStr: Var[Option[String]]): Signal[Element] =
    varOpStr
      .signal
      .map(_
        .map(dataStr => HtmlHelper.imageLoadedElement(classStr, dataStr))
        .getOrElse(HtmlHelper.imageLoadingElement(classStr))
      )

  def toElementSignalFullImg(classStr: String, varFullImg: Var[Option[FullImage]]): Signal[Element] =
    varFullImg
      .signal
      .map(_
        .map(fullImg => HtmlHelper.imageLoadedElement(classStr, fullImg.imgSourceString))
        .getOrElse(HtmlHelper.imageLoadingElement(classStr))
      )

  private def imageLoadingElement(divCls: String): Element = {
    div(
      cls := divCls,
      "Image has not loaded yet"
    )
  }

    private def imageLoadedElement(divCls: String, img: FullImage): Element = {
      div(
        cls := divCls,
        L.img(
          src := img.imgSourceString,
          styleAttr := "max-width: 100%; border: 1px solid #ccc;",
            L.onLoad  --> (_ => println("CALCED IMG LOADED (fullImg)")),
          onError --> (err => println("CALCED IMG ERROR, error:" + err.message))
        )
      )
    }

    private def imageLoadedElement(divCls: String, imgData: String): Element = {
      div(
        cls := divCls,
        L.img(
          src := imgData,
          styleAttr := "max-width: 100%; border: 1px solid #ccc;",
          onLoad  --> (_ => println("CALCED IMG LOADED (imgData)")),
          onError --> (err => println("CALCED IMG ERROR, prefix=" + imgData.take(60) + ", error: " + err.message))
        )
      )
    }

  def imagePreview(divCls: String, imgDesc: ImageDescription): Signal[Element] = {
    val fullImageVar = ImageStorage.loadFullImageIntoVar(imgDesc)(ExecutionContext.global)
    toElementSignalFullImg(divCls, fullImageVar)
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
