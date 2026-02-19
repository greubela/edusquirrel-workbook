package interactionPlugins.fileSubmission

import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.fileSubmission.turtleLogic.{TurtleRenderer, TurtleXmlParser}
import org.scalajs.dom.File
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.Thenable.Implicits.*
import scala.util.{Failure, Success, Try}

case class TurtleFileSubmission() extends HtmlAppElement {

  private given ExecutionContext = scala.concurrent.ExecutionContext.global

  def readBytes(file: File): Future[Array[Byte]] =
    file.arrayBuffer().toFuture.map { buffer =>
      val array = new Uint8Array(buffer)
      Array.tabulate(array.length)(i => array(i).toByte)
    }

  def renderFile(fileBytes: Array[Byte]): String = {
    val (existingPenTrailDataUrl, simulatedDataUrl) = renderFileAsTuple(fileBytes)
    if (existingPenTrailDataUrl.nonEmpty) existingPenTrailDataUrl else simulatedDataUrl
  }

  def renderFileAsTuple(fileBytes: Array[Byte]): (String, String) = {
    val xml = new String(fileBytes.map(_.toByte), "UTF-8")
    renderXmlAsTuple(xml)
  }

  def renderXmlAsTuple(xml: String): (String, String) = {
    val existingPenTrailDataUrl = extractPentrailsDataUrl(xml).getOrElse("")
    val commands = TurtleXmlParser.parse(xml)
    val simulatedDataUrl = TurtleRenderer.renderToPngDataUrl(commands)
    (existingPenTrailDataUrl, simulatedDataUrl)
  }

  private def extractPentrailsDataUrl(xml: String): Option[String] = {
    val hasDomParser = {
      scala.util.Try(scala.scalajs.js.Dynamic.global.selectDynamic("DOMParser")).toOption
        .exists(parser => !(scala.scalajs.js.isUndefined(parser) || parser == null))
    }

    val value =
      if (hasDomParser) {
        val parser = new dom.DOMParser()
        val document = parser.parseFromString(xml, "text/xml".asInstanceOf[dom.MIMEType])
        val pentrails = document.getElementsByTagName("pentrails")
        if (pentrails.length == 0) "" else Option(pentrails.item(0).textContent).map(_.trim).getOrElse("")
      } else {
        """(?s)<pentrails>\s*(.*?)\s*</pentrails>""".r
          .findFirstMatchIn(xml)
          .map(_.group(1).trim)
          .getOrElse("")
      }

    if (value.startsWith("data:image/png;base64,")) Some(value) else None
  }
  
  private val originalImageDataUrl = Var(Option.empty[String])
  private val simulatedImageDataUrl = Var(Option.empty[String])
  private val originalImageMessage = Var("Please upload a turtle XML file.")
  private val simulatedImageMessage = Var("Please upload a turtle XML file.")

  private val domElement: Element = div(
    styleAttr := "display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 1rem; align-items: start;",
    div(
      styleAttr := "display: flex; flex-direction: column; gap: 0.5rem;",
      h4("Upload"),
      input(
        typ := "file",
        accept := ".xml,text/xml",
        onChange --> { event =>
          val inputElement = event.target.asInstanceOf[dom.html.Input]
          val maybeFile = Option(inputElement.files).flatMap { files =>
            if (files.length > 0) Option(files.item(0)) else None
          }

          maybeFile match {
            case Some(file) =>
              originalImageDataUrl.set(None)
              simulatedImageDataUrl.set(None)
              originalImageMessage.set("Loading preview...")
              simulatedImageMessage.set("Loading simulation...")

              readBytes(file).onComplete {
                case Success(bytes) =>
                  Try(renderFileAsTuple(bytes)) match {
                    case Success((existingPenTrailDataUrl, simulatedDataUrl)) =>
                      val existingPenTrail = Option(existingPenTrailDataUrl).filter(_.nonEmpty)
                      val simulatedPenTrail = Option(simulatedDataUrl).filter(_.nonEmpty)

                      originalImageDataUrl.set(existingPenTrail)
                      simulatedImageDataUrl.set(simulatedPenTrail)

                      originalImageMessage.set(
                        if (existingPenTrail.isDefined) ""
                        else "Preview image is not available in the uploaded XML."
                      )
                      simulatedImageMessage.set(
                        if (simulatedPenTrail.isDefined) ""
                        else "Simulated image could not be created from the uploaded XML."
                      )

                    case Failure(_) =>
                      originalImageDataUrl.set(None)
                      simulatedImageDataUrl.set(None)
                      originalImageMessage.set("Preview image could not be created from this file.")
                      simulatedImageMessage.set("Simulated image could not be created from this file.")
                  }

                case Failure(_) =>
                  originalImageDataUrl.set(None)
                  simulatedImageDataUrl.set(None)
                  originalImageMessage.set("Could not read the selected file.")
                  simulatedImageMessage.set("Could not read the selected file.")
              }

            case None =>
              originalImageDataUrl.set(None)
              simulatedImageDataUrl.set(None)
              originalImageMessage.set("No file selected.")
              simulatedImageMessage.set("No file selected.")
          }
        }
      )
    ),
    div(
      h4("Preview (from XML pen trail)"),
      child <-- originalImageDataUrl.signal.combineWith(originalImageMessage.signal).map { case (maybeDataUrl, message) =>
        maybeDataUrl match {
          case Some(dataUrl) => img(src := dataUrl, styleAttr := "max-width: 100%; border: 1px solid #ccc;")
          case None => p(message)
        }
      }
    ),
    div(
      h4("Simulated"),
      child <-- simulatedImageDataUrl.signal.combineWith(simulatedImageMessage.signal).map { case (maybeDataUrl, message) =>
        maybeDataUrl match {
          case Some(dataUrl) => img(src := dataUrl, styleAttr := "max-width: 100%; border: 1px solid #ccc;")
          case None => p(message)
        }
      }
    )
  )
  
  override def getDomElement(): Element = domElement

}
