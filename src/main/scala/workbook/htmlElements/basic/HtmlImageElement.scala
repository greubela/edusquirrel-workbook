package workbook.htmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.web.file.{FileDescription, FullImage, LoadedFile}
import datastructures.web.storage.AsyncDataCache
import workbook.model.info.*

import scala.concurrent.ExecutionContext

case class HtmlImageElement(imageSignal: StrictSignal[Option[FullImage]], workbookInfo: AllWorkbookInfo) {
  
  def getDomSignal: Signal[Element] = imageSignal.map {
    case None => {
      span(
        text <-- workbookInfo.stringSignalFromLanguageMapId("basic/imageLoadingMap")(ExecutionContext.global)
      )
    }
    case Some(fullImg: FullImage) => {
      img(src := fullImg.imageSourceString, styleAttr := "max-width: 100%")
    }
  }

}

object HtmlImageElement {

  def apply(fullImage: FullImage, workbookInfo: AllWorkbookInfo): HtmlImageElement = {
    HtmlImageElement(Var(Some(fullImage)).signal, workbookInfo)
  }

  def apply(fileDescription: FileDescription, workbookInfo: AllWorkbookInfo): HtmlImageElement = {
    val fullImgVar: Var[Option[LoadedFile]] = workbookInfo.technicalElements.fileStore.loadIntoVariable(fileDescription)(ExecutionContext.global)
    val imageSignal: StrictSignal[Option[FullImage]] = fullImgVar.signal.mapLazy(_.map(_.toImage))
    HtmlImageElement(imageSignal, workbookInfo)
  }

}
