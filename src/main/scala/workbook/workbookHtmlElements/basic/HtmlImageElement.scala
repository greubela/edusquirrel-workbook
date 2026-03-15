package workbook.workbookHtmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.file.{FileDescription, FullImage, LoadedFile}
import contentmanagement.storage
import contentmanagement.storage.DataStorage
import workbook.model.info.*

import scala.concurrent.ExecutionContext

case class HtmlImageElement(imageSignal: StrictSignal[Option[FullImage]], workbookInfoVar: Var[WorkbookInfo]) {


  def getDomSignal(): Signal[Element] = imageSignal.map {
    case None => {
      span(
        text <-- DataStorage.labelSignalFromLanguageMapName("imageLoadingMap", workbookInfoVar)
      )
    }
    case Some(fullImg: FullImage) => {
      img(src := fullImg.imageSourceString, styleAttr := "max-width: 100%")
    }
  }

}

object HtmlImageElement {

  def apply(fullImage: FullImage, workbookInfoVar: Var[WorkbookInfo]): HtmlImageElement = {
    HtmlImageElement(Var(Some(fullImage)).signal, workbookInfoVar)
  }

  def apply(fileDescription: FileDescription, workbookInfoVar: Var[WorkbookInfo]): HtmlImageElement = {
    val fullImgVar: Var[Option[LoadedFile]] = DataStorage.fileDataStore.loadIntoVariable(fileDescription)(ExecutionContext.global)
    val imageSignal: StrictSignal[Option[FullImage]] = fullImgVar.signal.mapLazy(_.map(_.toImage))
    HtmlImageElement(imageSignal, workbookInfoVar)
  }

}
