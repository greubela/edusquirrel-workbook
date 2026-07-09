package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.RenderingWithCards.ElementCard
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, RenderingWithCards}
import it.evadid.workbook.elements.interactionElements.TurtleStitch.TurtleStitchExploreProjectElement

object HtmlTurtleStitchExploreProjectRenderer extends LineBasedRenderingFactory[TurtleStitchExploreProjectElement] {

  override protected def createRendering(workbookElement: TurtleStitchExploreProjectElement): AtomarLineRendering = {
    val contentCard1: List[Element] = List(
      HtmlTurtleStitchRendererHelper.renderProjectPreviewImage(workbookElement.projectToDownload),
      HtmlTurtleStitchRendererHelper.renderDownloadButton(LanguageMapContentId("TurtleStitch/downloadButton"), workbookElement.projectToDownload)
    )
    RenderingWithCards(false, List(ElementCard(LanguageMapContentId("TurtleStitch/providedProjectLabel"), contentCard1)))
  }
}
