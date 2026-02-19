package interactionPlugins.fileSubmission

import com.raquo.laminar.api.L.Element
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.fileSubmission.turtleLogic.{TurtleRenderer, TurtleXmlParser}
import org.scalajs.dom.File
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.Thenable.Implicits.*

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
  
  private val domElement: Element = ???
  
  override def getDomElement(): Element = domElement

}
