package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.homepage.control.HtmlFullWorkbookApp
import todomove.datastructures.web.file.FullImage
import todomove.datastructures.web.file.FullImage.LoadedFileImage
import it.evadid.homepage.webElements.*

import scala.concurrent.ExecutionContext
import it.evadid.homepage.webElements.*;

case class HtmlImageElement(imageSignal: Signal[Option[FullImage]]) extends HtmlAppElement {
  
  override def getDomElement(): Element = div(child <-- getDomSignal)
  
  def getDomSignal: Signal[Element] = imageSignal.map {
    case None => span(text <-- HtmlFullWorkbookApp.fullInfo.signals.stringFromLanguageMapId("basic/imageLoadingMap"))
    case Some(fullImg: FullImage) => img(src := fullImg.imageSourceString, styleAttr := "max-width: 100%")
  }
  
}

object HtmlImageElement {
    
  def apply(fullImage: FullImage): HtmlImageElement = {
    HtmlImageElement(Var(Some(fullImage)).signal)
  }

  def apply(fileDescription: FileDescription): HtmlImageElement = {
    val fullImgState: State[Option[LoadedFile]] = HtmlFullWorkbookApp.fullInfo.technical.fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global)
    val imageSignal: StrictSignal[Option[FullImage]] = fullImgState.toAirstreamVar.signal.mapLazy(_.map(LoadedFileImage(_)))
    HtmlImageElement(imageSignal)
  }

}
