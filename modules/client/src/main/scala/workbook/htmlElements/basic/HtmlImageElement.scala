package workbook.htmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.web.file.{FileDescription, FullImage, LoadedFile}
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.*
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
    val fullImgState: State[Option[LoadedFile]] = fullInfo.technical.fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global)
    val imageSignal: StrictSignal[Option[FullImage]] = fullImgState.toAirstreamVar.signal.mapLazy(_.map(_.toImage))
    HtmlImageElement(imageSignal, fullInfo)
  }

}
