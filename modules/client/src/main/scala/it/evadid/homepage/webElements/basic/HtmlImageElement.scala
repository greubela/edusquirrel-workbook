package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState, AsyncState, AsyncValue}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.webElements.*
import it.evadid.workbook.model.elements.ImageElement
import todomove.datastructures.web.file.FullImage.*
import todomove.datastructures.web.file.{FileFactory, FullImage}

import scala.concurrent.ExecutionContext

case class HtmlImageElement(imageSignal: AsyncData[Nothing, FullImage], underlyingImage: Option[ImageElement] = None) extends HtmlAppElement {

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

  def render(img: AsyncDataState[?, FullImage]): Element = img.match {
    case AsyncDataSuccess(img) => img.newDomImage
    case AsyncDataLoading() => renderImageLoading()
    case AsyncDataFailed(cause, data) => renderImageFailed(cause)
  }

  def getDomSignal: Signal[Element] = imageSignal.toStateSignal.map(render)

  override def getDomElement(): Element = div(child <-- getDomSignal)
}

object HtmlImageElement {

  private val fileStore = HtmlFullWorkbookApp.fullInfo.technical.fileStore
  private val signals = HtmlFullWorkbookApp.fullInfo.signals

  private def getImageSignal(image: ImageElement): AsyncData[Nothing, FullImage] = {
    val fileSignal: AsyncData[Nothing, LoadedFile] = image.match {
      case ImageElement.FileBasedImageElement(fileDescription) =>
        val obs = fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global).observeAllStates
        AsyncState(obs)
      case i@ImageElement.LanguageMapBasedImageElement(languageMapContentId, copyrightInfo, howToResolveUrl) =>
        val srcSignal: Signal[String] = signals.stringFromLanguageMapId(languageMapContentId)
        val srcFile: Signal[FileDescription] = srcSignal.map(FileFactory.resolve(howToResolveUrl, _))
        val res = srcFile.mapAsync(fileDesc => fileStore.loadAsFuture(fileDesc)(using ExecutionContext.global))(using ExecutionContext.global)
        res
    }
    fileSignal.map(loadedFile => LoadedFileImage(loadedFile))
  }

  def apply(fullImage: FullImage): HtmlImageElement = {
    val imageContext: Option[ImageElement] = fullImage.match {
      case LoadedFileImage(loadedFile) => Some(ImageElement.FileBasedImageElement(loadedFile.description))
      case _ => None
    }
    HtmlImageElement(AsyncValue(fullImage), imageContext)
  }

  def apply(fileDescription: FileDescription): HtmlImageElement = {
    HtmlImageElement(ImageElement.FileBasedImageElement(fileDescription))
  }

  def apply(imageElement: ImageElement): HtmlImageElement = {
    HtmlImageElement(getImageSignal(imageElement), Some(imageElement))
  }

}
