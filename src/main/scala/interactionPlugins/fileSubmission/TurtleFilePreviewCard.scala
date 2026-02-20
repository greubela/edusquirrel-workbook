package interactionPlugins.fileSubmission

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.fileSubmission.turtleStitch.TurtleStitchXmlLoader
import org.scalajs.dom
import util.HtmlHelper
import workbook.model.info.WorkbookInfo
import workbook.model.interaction.InteractionVariable
import workbook.model.interaction.history.UpdateImportance

import scala.util.Try

case class TurtleFilePreviewCard(workbookInfoVar: L.Var[WorkbookInfo],
                                 xmlFileInteractionVar: InteractionVariable[String]) extends HtmlAppElement {

  private def emptyPreview(): List[Element] = List(
    h3(
      child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(TurtleStitchFileUploadFactory.languageMapShowEmptyPreview.getInLanguage)
    ),
    div(
      cls := "preview-content",
      child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(TurtleStitchFileUploadFactory.languageMapShowEmptyDescription.getInLanguage)
    )
  )

  private def pentrailPreview(pentrailData: String): List[Element] = List(
    h3(
      child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(TurtleStitchFileUploadFactory.languageMapShowPentrailPreview.getInLanguage)
    ),
    div(
      cls := "preview-content",
      img(src := pentrailData, styleAttr := "max-width: 100%; border: 1px solid #ccc;")
    )
  )

  private def thumbnailPreview(thumbnailData: String): List[Element] = List(
    h3(
      child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(TurtleStitchFileUploadFactory.languageMapShowThumbnailPreview.getInLanguage)
    ),
    div(
      cls := "preview-content",
      img(src := thumbnailData, styleAttr := "max-width: 100%; border: 1px solid #ccc;")
    )
  )

  private def errorPreview(messages: List[String]): List[Element] = List(
    h3(
      child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(TurtleStitchFileUploadFactory.languageMapShowErrorPreview.getInLanguage)
    ),
    div(
      cls := "preview-content",
      messages.mkString(" ### ")
    )
  )

  private lazy val downloadButton: Element = button(
    child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(TurtleStitchFileUploadFactory.languageMapDownloadButton.getInLanguage),
    onClick --> { _ =>
      val curEvent = xmlFileInteractionVar.lastUpdate
      HtmlHelper.downloadFile("TurtleStitch_" + xmlFileInteractionVar.underlyingInteraction.id + "_" + curEvent.epochTimestampMillis + ".xml", curEvent.value)
    }
  )

  private val fileImagePreview: Signal[List[Element]] = xmlFileInteractionVar.interactionSignal.map(newXml => {
    //print("new Xml: " + newXml)
    val res = if (newXml.trim.isEmpty) {
      emptyPreview()
    } else {
      val pentrail = getPentrailDataString(newXml)
      if (pentrail.isRight && pentrail.right.get.nonEmpty) pentrailPreview(pentrail.right.get.get)
      else {
        val thumbnail = getThumbnailDataString(newXml)
        if (thumbnail.isRight && thumbnail.right.get.nonEmpty) thumbnailPreview(thumbnail.right.get.get)
        else {
          val penErrMsg = if (pentrail.isLeft) Some("Pentrail Error: '" + pentrail.left.get.getMessage + "'") else None
          val thumbErrMsg = if (thumbnail.isLeft) Some("Thumbnail Error: '" + thumbnail.left.get.getMessage + "'") else None
          val penEmptyMsg = if (thumbnail.isRight && thumbnail.right.get.isEmpty) Some("Thumbnail: empty") else None
          val thumbEmptyMsg = if (pentrail.isRight && pentrail.right.get.isEmpty) Some("Pentrail: empty") else None

          errorPreview(List(penErrMsg, thumbErrMsg, penEmptyMsg, thumbEmptyMsg).flatten)
        }
      }
    }
    val downOp: Option[Element] = if (newXml.isEmpty) None else Some(downloadButton)
    res ++ downOp
  })

  private val domElement: Element = div(
    cls := "preview-card",
    children <-- fileImagePreview
  )

  override def getDomElement(): Element = domElement

  def getThumbnailDataString(xml: String): Either[Throwable, Option[String]] = {
    Try(
      TurtleStitchXmlLoader.load(xml)
    ).map(project => {
      project.thumbnail
        .filter(_.startsWith("data:image/png;base64,"))
    }).toEither
  }

  def getPentrailDataString(xml: String): Either[Throwable, Option[String]] = {
    Try(
      TurtleStitchXmlLoader.load(xml)
    ).map(project => {
      project.scenes
        .lift(project.selectedScene - 1)
        .orElse(project.scenes.headOption)
        .flatMap(_.stage.pentrails)
        .filter(_.startsWith("data:image/png;base64,"))
    }).toEither
  }


  private def logError(message: String, error: Throwable): Unit =
    dom.console.error(s"[Error] $message", error)

  private def logError(message: String): Unit =
    dom.console.error(s"[Error] $message")

}
