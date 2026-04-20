package workbook.htmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.web.file.{FileDescription, FullImage, LoadedFile}
import datastructures.web.storage.AsyncDataCache
import workbook.model.info.*

import scala.concurrent.ExecutionContext

case class HtmlImageElement(imageSignal: StrictSignal[Option[FullImage]], fullInfo: FullInfo) {

  def getDomSignal: Signal[Element] = imageSignal.map {
    case None => {
      span(text <-- fullInfo.signals.stringFromLanguageMapId("basic/imageLoadingMap"))
    }
    case Some(fullImg: FullImage) => {
      img(src := fullImg.imageSourceString, styleAttr := "max-width: 100%")
    }
  }

}

object HtmlImageElement {

  def apply(fullImage: FullImage, fullInfo: FullInfo): HtmlImageElement = {
    HtmlImageElement(Var(Some(fullImage)).signal, fullInfo)
  }

  def apply(fileDescription: FileDescription, fullInfo: FullInfo): HtmlImageElement = {
    val fullImgVar: Var[Option[LoadedFile]] = fullInfo.technical.fileStore.loadIntoVariable(fileDescription)(ExecutionContext.global)
    val imageSignal: StrictSignal[Option[FullImage]] = fullImgVar.signal.mapLazy(_.map(_.toImage))
    HtmlImageElement(imageSignal, fullInfo)
  }

}
