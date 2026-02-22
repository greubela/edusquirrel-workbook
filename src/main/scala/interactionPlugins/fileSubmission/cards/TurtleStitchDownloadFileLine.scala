package interactionPlugins.fileSubmission.cards

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.ImageDescription
import interactionPlugins.fileSubmission.cards.TurtleFileButtonCard.StorageFormat
import org.scalajs.dom.URL
import workbook.model.info.WorkbookInfo
import workbook.model.interaction.InteractionVariable
import workbook.workbookHtmlElements.abstractions.WorkbookInteraction

case class TurtleStitchDownloadFileLine(workbookInfoVar: Var[WorkbookInfo], id: String, existingProject: URL, existingProjectImg: ImageDescription) extends WorkbookInteraction[String] {

  private val fileInteraction = TurtleFileButtonCard(workbookInfoVar, id, List(".xml,text/xml"), StorageFormat.BYTES_AS_RAW_STRING)
  override val interactionVariable: InteractionVariable[String] = fileInteraction.interactionVariable
  private val fileImagePreview = TurtleFilePreviewCard(workbookInfoVar, interactionVariable)
  private val existingProjectView = TurtleFileExistingProjectCard(workbookInfoVar, fileInteraction.interactionVariable.underlyingInteraction.id, "filename", existingProjectImg, existingProject)

  private val domElement: Element = div(
    cls := "workbook-interaction preview-line",
    existingProjectView.getDomElement(),
    fileInteraction.getDomElement(),
    fileImagePreview.getDomElement(),
  )


  override def getDomElement(): L.Element = domElement
}
