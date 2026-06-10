package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.interaction.plugins.TurtleStitch.TurtleStitchRecreateShapeInteraction

object HtmlTurtleStitchRecreateShapeRenderer extends HtmlRenderFactory[TurtleStitchRecreateShapeInteraction] {

  override protected def createDomElement(workbookElement: TurtleStitchRecreateShapeInteraction): Element = {
    div(
      cls := "workbook-interaction preview-line",
      div(
        cls := "preview-card",
        HtmlTurtleStitchRendererHelper.cardHeadline(LanguageMapContentId("TurtleStitch/showExpected")),
        HtmlImageElement(workbookElement.imageToRecreate).getDomElement()
      ),
      div(
        cls := "preview-card",
        HtmlTurtleStitchRendererHelper.cardHeadline(LanguageMapContentId("TurtleStitch/uploadTitle")),
        HtmlTurtleStitchRendererHelper.renderUploadButton(workbookElement)
      ),
      div(
        cls := "preview-card",
        HtmlTurtleStitchRendererHelper.cardHeadline(LanguageMapContentId("TurtleStitch/showUploadedProgramText")),
        HtmlTurtleStitchRendererHelper.renderProjectPreviewImage(workbookElement),
        HtmlTurtleStitchRendererHelper.renderDownloadButton(LanguageMapContentId("TurtleStitch/redownloadProgram"), workbookElement)
      )
    )

  }



}
