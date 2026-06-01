package it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import it.evadid.homepage.control.info.FullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch.TurtleStitchExpectedShapeCard
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.card.TurtleStitchFileUploadButtonCard
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.card.TurtleStitchFileUploadButtonCard.StorageFormat


object TurtleStitchRecreateShapeExercise {

/*
  def createInteractionElement(
                                pWorkbookInfo: FullInfo,
                                baseId: String,
                                expectedOutcome: HtmlImageElement
                              ): HtmlAppElement = {

    def fullInfo: FullInfo = pWorkbookInfo

    private val fileInteraction = TurtleStitchFileUploadButtonCard(pWorkbookInfo, baseId, List(".xml,text/xml"), StorageFormat.BYTES_AS_RAW_STRING)

    //val preview: HtmlWorkbookElement = TurtleFileShowProgramXmlCard(fileInteraction)

    private val expectedOutcomePreview = TurtleStitchExpectedShapeCard(expectedOutcome)

    override def getDomElement(): L.Element = div(
      cls := "workbook-interaction preview-line",
      expectedOutcomePreview.getDomElement(),
      fileInteraction.getDomElement(),
      //  preview.getDomElement()
    )
  }*/

}


