package interactionPlugins.fileSubmission


import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.{FullImage, ImageDescription}
import contentmanagement.storage.ImageStorage
import contentmanagement.webElements.HtmlAppElement

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.*

case class TurtleFileExpectedCard(expectedOutcome: ImageDescription) extends HtmlAppElement {

  val loadedImage: Var[Option[FullImage]] = Var(None)

  def imageLoadingElement(): List[Element] = List(
    h3("Expected Outcome"),
    div(
      cls := "preview-content",
      "Image has not loaded yet")
  )

  def imageLoadedElement(img: FullImage): List[Element] = List(
    h3("Expected Outcome"),
    div(
      cls := "preview-content",
      L.img(src <-- loadedImage.signal.map(_.get.imgSourceString), styleAttr := "max-width: 100%; border: 1px solid #ccc;")
    )
  )

  private val domElementSignal: Signal[List[Element]] = {
    loadedImage.signal.map {
      case None => imageLoadingElement()
      case Some(img) => imageLoadedElement(img)
    }
  }

  ImageStorage.loadFullImage(expectedOutcome).onComplete {
    case Success(fullImg) => loadedImage.update(_ => Some(fullImg))
    case Failure(error) => println("ERROR at loading img :-(")
  }

  private val domElement: Element = div(
    cls := "preview-card",
    children <-- domElementSignal,
  )

  override def getDomElement(): Element = domElement
}
