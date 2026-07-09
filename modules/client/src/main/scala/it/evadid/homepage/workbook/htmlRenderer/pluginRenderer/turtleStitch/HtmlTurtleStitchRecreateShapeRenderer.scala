package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.HtmlWorkbookElement
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.RenderingWithCards.ElementCard
import it.evadid.workbook.elements.interactionElements.TurtleStitch.TurtleStitchRecreateShapeInteraction

object HtmlTurtleStitchRecreateShapeRenderer extends LineBasedRenderingFactory[TurtleStitchRecreateShapeInteraction] {


  override protected def createRendering(workbookElement: TurtleStitchRecreateShapeInteraction): AtomarLineRendering = {

    RenderingWithCards(true, List(
      ElementCard(
        LanguageMapContentId("TurtleStitch/showExpected"),
        HtmlImageElement(workbookElement.imageToRecreate).getDomElement()
      ),
      ElementCard(
        LanguageMapContentId("TurtleStitch/uploadTitle"),
        HtmlTurtleStitchRendererHelper.renderUploadButton(workbookElement)
      ),
      ElementCard(
        LanguageMapContentId("TurtleStitch/showUploadedProgramText"),
        List(
          HtmlTurtleStitchRendererHelper.renderProjectPreviewImage(workbookElement),
          HtmlTurtleStitchRendererHelper.renderDownloadButton(LanguageMapContentId("TurtleStitch/redownloadProgram"), workbookElement)
        )
      )
    )
    )


  }
}
