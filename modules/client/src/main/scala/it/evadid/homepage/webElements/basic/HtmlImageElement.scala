package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.homepage.control.{HtmlFullWorkbookApp, WorkbookContentStorage}
import it.evadid.homepage.webElements.*
import it.evadid.workbook.model.abstractions.TypeOfTextContent
import it.evadid.workbook.model.elements.ImageElement
import it.evadid.workbook.model.elements.ImageElement.LanguageMapBasedImageElement
import todomove.datastructures.web.file.FullImage
import todomove.datastructures.web.storage.AsyncData;

case class HtmlImageElement(imageSignal: Signal[AsyncData[FullImage]], underlyingImage: Option[ImageElement] = None) extends HtmlAppElement {

  override def getDomElement(): Element = div(child <-- getDomSignal)


  private def stringSignal(id: LanguageMapContentId): Signal[String] =
    HtmlFullWorkbookApp.fullInfo.signals.stringFromLanguageMapId(id)

  private def stringSignal(map: LanguageMap[HumanLanguage]): Signal[String] =
    HtmlFullWorkbookApp.fullInfo.signals.stringFromLanguageMap(map)

  private def renderImageLoading(): Element = {
    div(text <-- stringSignal(LanguageMapContentId("basic/imageLoadingMap")))
  }

  private def renderImageFailed(cause: Throwable): Element = {
    val map = WorkbookContentStorage.languageMapImageError(underlyingImage, cause)
    div("Image loading failed: " + cause.getMessage)
  }

  def render(img: AsyncData[FullImage]): Element = img.match {
    case AsyncData.AsyncDataSuccess(img) => img.newDomImage
    case AsyncData.AsyncDataLoading => renderImageLoading()
    case AsyncData.AsyncDataFailed(cause) => renderImageFailed(cause)
    case e:Any => div("This should be unreachable but: " + e)
  }

  def getDomSignal: Signal[Element] = imageSignal.map(render)

}

object HtmlImageElement {

  private def getImageSignal(image: ImageElement): Signal[Either[Option[FullImage], Throwable]] = {
    /*imageElement.match {
      case ImageElement.FileBasedImageElement(location) => {
        val fullImgState: State[Option[LoadedFile]] = HtmlFullWorkbookApp.fullInfo.technical.fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global)
        val imageSignal: StrictSignal[Option[FullImage]] = fullImgState.toAirstreamVar.signal.mapLazy(_.map(LoadedFileImage(_)))
      }
      case i@ImageElement.LanguageMapBasedImageElement(languageMapContentId, copyrightInfo, howToResolveUrl) => HtmlImageElement(i)
    }*/
    ???
  }

  def apply(fullImage: FullImage): HtmlImageElement = {
    // HtmlImageElement(Var(Some(fullImage)).signal)
    ???
  }

  def apply(fileDescription: FileDescription): HtmlImageElement = {
    // HtmlImageElement(imageSignal)
    ???
  }

  def apply(imageElement: ImageElement): HtmlImageElement = {
    imageElement.match {
      case ImageElement.FileBasedImageElement(location) => HtmlImageElement(location)
      case i@ImageElement.LanguageMapBasedImageElement(languageMapContentId, copyrightInfo, howToResolveUrl) => HtmlImageElement(i)
    }
  }

  def apply(image: LanguageMapBasedImageElement): HtmlImageElement = {
    val srcSignal: Signal[String] = HtmlFullWorkbookApp.fullInfo.signals.stringFromLanguageMapId(image.languageMapContentId)
    image.howToResolveUrl.match {
      case TypeOfTextContent.URL_RELATIVE_TO_GLOBAL_RESOURCES => ???
      case TypeOfTextContent.URL_RELATIVE_TO_WORKBOOK_RESOURCES(workbookRoot) => ???
    }
  }

}
