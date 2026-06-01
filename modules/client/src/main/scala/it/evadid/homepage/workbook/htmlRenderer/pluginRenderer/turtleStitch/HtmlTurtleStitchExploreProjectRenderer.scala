package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.plugins.TurtleStitch.TurtleStitchExploreProjectElement

object HtmlTurtleStitchExploreProjectRenderer extends HtmlRenderFactory[TurtleStitchExploreProjectElement] {

  override protected def createDomElement(workbookElement: TurtleStitchExploreProjectElement): Element = {
    div(
      cls := "workbook-interaction preview-line",
      div(
        cls := "preview-card",
        HtmlTurtleStitchRendererHelper.cardHeadline(LanguageMapContentId("TurtleStitch/providedProjectLabel")),
        HtmlTurtleStitchRendererHelper.renderProjectPreviewImage(workbookElement.projectToDownload),
        HtmlTurtleStitchRendererHelper.renderDownloadButton(LanguageMapContentId("TurtleStitch/downloadButton"), workbookElement.projectToDownload)
      )
    )
  }


}
