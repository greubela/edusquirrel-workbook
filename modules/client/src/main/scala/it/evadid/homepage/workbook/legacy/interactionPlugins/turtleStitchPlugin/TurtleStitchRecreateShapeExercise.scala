package it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.card.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.card.TurtleStitchFileUploadButtonCard.StorageFormat
import it.evadid.core.datastructures.file.*
import it.evadid.homepage.workbook.legacy.htmlElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.card.{TurtleFileShowProgramXmlCard, TurtleStitchExpectedShapeCard, TurtleStitchFileUploadButtonCard}
import it.evadid.homepage.workbook.legacy.model.abstractions.HtmlWorkbookElement
import it.evadid.homepage.workbook.legacy.model.info.FullInfo

import scala.concurrent.ExecutionContext

object TurtleStitchRecreateShapeExercise {


  def createInteractionElement(
                                pWorkbookInfo: FullInfo,
                                baseId: String,
                                expectedOutcome: HtmlImageElement
                              ): HtmlWorkbookElement = {
    new HtmlWorkbookElement() {

      def fullInfo: FullInfo = pWorkbookInfo

      private val fileInteraction = TurtleStitchFileUploadButtonCard(pWorkbookInfo, baseId, List(".xml,text/xml"), StorageFormat.BYTES_AS_RAW_STRING)

      val preview: HtmlWorkbookElement = TurtleFileShowProgramXmlCard(fileInteraction)

      private val expectedOutcomePreview = TurtleStitchExpectedShapeCard(pWorkbookInfo, expectedOutcome)

      override def getDomElement(): L.Element = div(
        cls := "workbook-interaction preview-line",
        expectedOutcomePreview.getDomElement(),
        fileInteraction.getDomElement(),
        preview.getDomElement()
      )
    }
  }



}
