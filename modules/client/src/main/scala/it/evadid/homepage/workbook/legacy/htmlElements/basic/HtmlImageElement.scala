package it.evadid.homepage.workbook.legacy.htmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.homepage.workbook.legacy.model.info.*
import it.evadid.homepage.workbook.legacy.model.info.FullInfo
import todomove.datastructures.web.file.FullImage
import todomove.datastructures.web.file.FullImage.LoadedFileImage

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
    val fullImgState: State[Option[LoadedFile]] = fullInfo.technical.fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global)
    val imageSignal: StrictSignal[Option[FullImage]] = fullImgState.toAirstreamVar.signal.mapLazy(_.map(LoadedFileImage(_)))
    HtmlImageElement(imageSignal, fullInfo)
  }

}
