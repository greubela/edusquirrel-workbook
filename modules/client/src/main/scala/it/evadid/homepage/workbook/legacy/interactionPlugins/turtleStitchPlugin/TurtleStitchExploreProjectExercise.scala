package it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin

import it.evadid.core.datastructures.file.FileDescription
import it.evadid.homepage.workbook.legacy.htmlElements.interactions.HtmlBasicTextInteraction
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.card.TurtleFileShowProgramXmlCard
import it.evadid.homepage.workbook.legacy.model.abstractions.HtmlWorkbookElement
import it.evadid.homepage.workbook.legacy.model.info.FullInfo

import scala.concurrent.ExecutionContext

object TurtleStitchExploreProjectExercise {

  def createElementLine(workbookInfo: FullInfo, projectToDownload: FileDescription): HtmlWorkbookElement =
    TurtleFileShowProgramXmlCard(workbookInfo, projectToDownload).asWorkbookElement

}
