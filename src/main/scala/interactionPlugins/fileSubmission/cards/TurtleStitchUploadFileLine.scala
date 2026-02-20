package interactionPlugins.fileSubmission.cards

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.ImageDescription
import interactionPlugins.fileSubmission.cards.TurtleFileButtonCard.StorageFormat
import workbook.model.info.WorkbookInfo
import workbook.model.interaction.InteractionVariable
import workbook.workbookHtmlElements.abstractions.WorkbookInteraction

case class TurtleStitchUploadFileLine(workbookInfoVar: Var[WorkbookInfo], id: String, expectedOutcome: ImageDescription) extends WorkbookInteraction[String] {

  private val fileInteraction = TurtleFileButtonCard(workbookInfoVar, id, List(".xml,text/xml"), StorageFormat.BYTES_AS_RAW_STRING)

  override val interactionVariable: InteractionVariable[String] = fileInteraction.interactionVariable

  private val fileImagePreview = TurtleFilePreviewCard(workbookInfoVar, interactionVariable)

  private val expectedOutcomePreview = TurtleFileExpectedCard(workbookInfoVar, expectedOutcome)

  private val domElement: Element = div(
    cls := "workbook-interaction preview-line",
    fileInteraction.getDomElement(),
    fileImagePreview.getDomElement(),
    expectedOutcomePreview.getDomElement()
  )


  override def getDomElement(): L.Element = domElement
}
