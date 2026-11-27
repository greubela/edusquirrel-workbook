package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.homepage.control.model.FullInfo

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleStitchRecreateShapeInteractionLegacy

object HtmlTurtleStitchRecreateShapeRendererLegacy extends LineBasedRenderingFactory[TurtleStitchRecreateShapeInteractionLegacy] {

  override protected def createRendering(fullInfo: FullInfo, workbookElement: TurtleStitchRecreateShapeInteractionLegacy): AtomarLineRendering = {

    val imageToRecreate = fullInfo.contentControl.fileFactory.relativeToTechnicalResources(workbookElement.filenameRelToResources)

    AtomarLineRendering.cardLine(fullInfo, workbookElement, List(
      ElementCard(
        fullInfo, LanguageMapContentId("TurtleStitch/showExpected"),
        HtmlImageElement(fullInfo, imageToRecreate).getDomElement()
      ),
      ElementCard(
        fullInfo, LanguageMapContentId("TurtleStitch/uploadTitle"),
        HtmlTurtleStitchRendererHelper.renderUploadButton(fullInfo, workbookElement)
      ),
      ElementCard(
        fullInfo, LanguageMapContentId("TurtleStitch/showUploadedProgramText"),
        List(
          HtmlTurtleStitchRendererHelper.renderProjectPreviewImage(fullInfo, workbookElement),
          HtmlTurtleStitchRendererHelper.renderDownloadButton(fullInfo, LanguageMapContentId("TurtleStitch/redownloadProgram"), workbookElement)
        )
      )
    )
    )


  }
}
