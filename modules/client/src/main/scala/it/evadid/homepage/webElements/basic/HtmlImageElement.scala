package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.state.{ObservableValue, State}
import it.evadid.core.datastructures.storage.AsyncData
import it.evadid.core.datastructures.storage.AsyncData.AsyncDataSuccess
import it.evadid.homepage.control.{HtmlFullWorkbookApp, WorkbookContentStorage}
import it.evadid.homepage.webElements.*
import it.evadid.workbook.model.abstractions.TypeOfTextContent
import it.evadid.workbook.model.elements.ImageElement
import it.evadid.workbook.model.elements.ImageElement.LanguageMapBasedImageElement
import todomove.datastructures.web.file.FullImage.*
import todomove.datastructures.web.file.{FileFactory, FullImage}

import scala.concurrent.ExecutionContext

case class HtmlImageElement(imageSignal: ObservableValue[AsyncData[FullImage]], underlyingImage: Option[ImageElement] = None) extends HtmlAppElement {

  private def stringSignal(id: LanguageMapContentId): Signal[String] =
    HtmlFullWorkbookApp.fullInfo.signals.stringFromLanguageMapId(id)

  private def stringSignal(map: LanguageMap[HumanLanguage]): Signal[String] =
    HtmlFullWorkbookApp.fullInfo.signals.stringFromLanguageMap(map)

  private def renderImageLoading(): Element = {
    div(text <-- stringSignal(LanguageMapContentId("basic/imageLoadingMap")))
  }

  private def renderImageFailed(cause: Throwable): Element = {
    //val map = WorkbookContentStorage.languageMapImageError(underlyingImage, cause)
    //div("Image loading failed: " + cause.getMessage)
    div(text <-- stringSignal(LanguageMapContentId("basic/missingContent")))
  }

  def render(img: AsyncData[FullImage]): Element = img.match {
    case AsyncData.AsyncDataSuccess(img) => img.newDomImage
    case AsyncData.AsyncDataLoading() => renderImageLoading()
    case AsyncData.AsyncDataFailed(cause) => renderImageFailed(cause)
  }

  def getDomSignal: Signal[Element] = imageSignal.toSignal.map(render)

  override def getDomElement(): Element = div(child <-- getDomSignal)
}

object HtmlImageElement {

  private val fileStore = HtmlFullWorkbookApp.fullInfo.technical.fileStore
  private val signals = HtmlFullWorkbookApp.fullInfo.signals

  private def getImageSignal(image: ImageElement): ObservableValue[AsyncData[FullImage]] = {
    val fileSignal: ObservableValue[AsyncData[LoadedFile]] = image.match {
      case ImageElement.FileBasedImageElement(fileDescription) =>
        fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global).observable
      case i@ImageElement.LanguageMapBasedImageElement(languageMapContentId, copyrightInfo, howToResolveUrl) =>
        val srcSignal: Signal[String] = signals.stringFromLanguageMapId(languageMapContentId)
        val srcFile: Signal[FileDescription] = srcSignal.map(FileFactory.resolve(howToResolveUrl, _))
        srcFile.mapAsync(fileDesc => fileStore.loadAsFuture(fileDesc)(using ExecutionContext.global))(using ExecutionContext.global)
    }
    fileSignal.map(loadedFile => LoadedFileImage(loadedFile))
  }

  def apply(fullImage: FullImage): HtmlImageElement = fullImage.match {
    case DataSourceImage(dataSource: String, fileFormat: String) =>
      HtmlImageElement(State[AsyncData[FullImage]](AsyncDataSuccess(fullImage)).observable, None)
    case LoadedFileImage(loadedFile) =>
      val imageElement = ImageElement.FileBasedImageElement(loadedFile.description)
      HtmlImageElement(State[AsyncData[FullImage]](AsyncDataSuccess(fullImage)).observable, Some(imageElement))

  }

  def apply(fileDescription: FileDescription): HtmlImageElement = {
    HtmlImageElement(ImageElement.FileBasedImageElement(fileDescription))
  }

  def apply(imageElement: ImageElement): HtmlImageElement = {
    HtmlImageElement(getImageSignal(imageElement), Some(imageElement))
  }

}
