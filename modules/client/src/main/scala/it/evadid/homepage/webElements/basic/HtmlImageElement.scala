package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.async.AsyncDataState
import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.distribution.command.SerializedException
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.*
import it.evadid.homepage.webElements.basic.FullImage.*
import it.evadid.workbook.elements.display.WorkbookImageElement

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/** An image whose loading states come from `images`; nothing is observed while the element is unmounted. */
case class HtmlImageElement(fullInfo: FullInfo, images: Signal[AsyncDataState[?, FullImage]], underlyingImage: Option[WorkbookImageElement] = None) extends HtmlAppElement {

  private def message(id: String): Element = div(text <-- fullInfo.signals.stringFromLanguageMapId(LanguageMapContentId(id)))

  def render(img: AsyncDataState[?, FullImage]): Element = img.match {
    case AsyncDataSuccess(img) => img.newDomImage
    case AsyncDataLoading(_) => message("basic/imageLoadingMap")
    case AsyncDataFailed(_, _) => message("basic/missingContent")
  }

  /** The element ID lets workbook styles address a single figure. */
  override def getDomElement(): Element = div(cls := "workbook-image",
    underlyingImage.map(image => dataAttr("element-id") := image.elementId), child <-- images.map(render))
}

object HtmlImageElement {

  /** Loading, then the loaded file or its failure. */
  private def loading(file: Future[LoadedFile]): Signal[AsyncDataState[Nothing, FullImage]] =
    Signal.fromFuture(file.transform(Success(_))).map {
      case None => AsyncDataLoading[Nothing, FullImage]()
      case Some(Success(loaded)) => AsyncDataSuccess[Nothing, FullImage](LoadedFileImage(loaded))
      case Some(Failure(error)) => AsyncDataFailed[Nothing, FullImage](SerializedException(error), None)
    }

  def apply(fullInfo: FullInfo, fullImage: FullImage): HtmlImageElement =
    HtmlImageElement(fullInfo, Signal.fromValue(AsyncDataSuccess[Nothing, FullImage](fullImage)), None)

  def apply(fullInfo: FullInfo, fileDescription: FileDescription): HtmlImageElement =
    HtmlImageElement(fullInfo, loading(fileDescription.loadData()), None)

  /** A workbook image follows its language-map entry: a changed source loads the new file. */
  def apply(fullInfo: FullInfo, image: WorkbookImageElement): HtmlImageElement = image match {
    case WorkbookImageElement.LanguageMapBasedWorkbookImageElement(_, contentId, howToResolveUrl, _, _) =>
      val files = fullInfo.signals.stringFromLanguageMapId(contentId).distinct
        .map(fullInfo.contentControl.fileFactory.resolveFromTypeAndLanguageMapContent(howToResolveUrl, _))
      HtmlImageElement(fullInfo, files.flatMapSwitch(file => loading(file.loadData())), Some(image))
  }
}
