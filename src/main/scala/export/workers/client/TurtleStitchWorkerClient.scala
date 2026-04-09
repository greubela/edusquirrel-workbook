package `export`.workers.client

import `export`.traits.AbstractWorkerClient
import org.scalajs.dom.html.Canvas

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

case class TurtleStitchWorkerClient(canvas: Canvas)
    extends AbstractWorkerClient("startTurtleWorkerServer", Map("hi" -> "bye"), Some(canvas)) {

  def snapshotGreenFlagProgramsPngDataUrl(xml_content: String, language: String = "en"): Future[String] =
    enqueue(
      "snapshotGreenFlagProgramsPngDataUrl",
      Map(
        "xml_content" -> xml_content,
        "language" -> language
      )
    ).map(_.data.getOrElse("value", ""))

  def getGreenFlagAsLispCode(xml_content: String, language: String): Future[String] =
    enqueue(
      "getGreenFlagAsLispCode",
      Map(
        "xml_content" -> xml_content,
        "language" -> language
      )
    ).map(_.data.getOrElse("value", ""))

}
