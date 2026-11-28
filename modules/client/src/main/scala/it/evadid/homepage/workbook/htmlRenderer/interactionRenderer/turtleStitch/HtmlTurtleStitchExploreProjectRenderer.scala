package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.TurtleStitchExploreProjectElement

object HtmlTurtleStitchExploreProjectRenderer extends LineBasedRenderingFactory[TurtleStitchExploreProjectElement] {

  override protected def createRendering(fullInfo: FullInfo, workbookElement: TurtleStitchExploreProjectElement): AtomarLineRendering = {
    val contentCard1: Seq[Element] = List(
      HtmlTurtleStitchRendererHelper.renderProjectPreviewImage(fullInfo, workbookElement.projectPathRelToResources),
      HtmlTurtleStitchRendererHelper.renderDownloadButton(fullInfo, LanguageMapContentId("TurtleStitch/downloadButton"), workbookElement.projectPathRelToResources)
    )
    AtomarLineRendering.cardLine(fullInfo, workbookElement, List(ElementCard(fullInfo, LanguageMapContentId("TurtleStitch/providedProjectLabel"), contentCard1)))
  }
}
