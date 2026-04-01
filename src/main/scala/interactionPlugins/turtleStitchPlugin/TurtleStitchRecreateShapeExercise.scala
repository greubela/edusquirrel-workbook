package interactionPlugins.turtleStitchPlugin

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import datastructures.web.file.FullImage
import interactionPlugins.turtleStitchPlugin.card.*
import interactionPlugins.turtleStitchPlugin.card.TurtleStitchFileUploadButtonCard.StorageFormat
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

import scala.concurrent.ExecutionContext

object TurtleStitchRecreateShapeExercise {


  def createInteractionElement(
                                pWorkbookInfo: AllWorkbookInfo,
                                baseId: String,
                                expectedOutcome: FullImage
                              ): HtmlWorkbookElement = {
    new HtmlWorkbookElement() {

      def workbookInfo: AllWorkbookInfo = pWorkbookInfo

      private val fileInteraction = TurtleStitchFileUploadButtonCard(pWorkbookInfo, baseId, List(".xml,text/xml"), StorageFormat.BYTES_AS_RAW_STRING)

      val preview: HtmlWorkbookElement = TurtleFileShowProgramXmlCard(fileInteraction)

      private val expectedOutcomePreview = TurtleStitchExpectedShapeCard(pWorkbookInfo, expectedOutcome)

      override def getDomElement(): L.Element = div(
        cls := "workbook-interaction preview-line",
        fileInteraction.getDomElement(),
        preview.getDomElement(),
        expectedOutcomePreview.getDomElement()
      )
    }
  }

  def createElements(
                      pWorkbookInfo: AllWorkbookInfo,
                      baseId: String,
                      titleLanguageMapId: String,
                      expectedOutcome: FullImage
                    ): List[HtmlWorkbookElement] = {

    val htmlTitleElement = HtmlContainerTitle(pWorkbookInfo, titleLanguageMapId)

    val instr = HtmlPlaintextInstructionElement(pWorkbookInfo, pWorkbookInfo.stringSignalFromLanguageMapId("TurtleStitch/defaultReprogramInstruction")(ExecutionContext.global))

    val uploadLine: HtmlWorkbookElement = createInteractionElement(pWorkbookInfo, baseId, expectedOutcome)

    List(htmlTitleElement, instr, uploadLine)
  }


}
