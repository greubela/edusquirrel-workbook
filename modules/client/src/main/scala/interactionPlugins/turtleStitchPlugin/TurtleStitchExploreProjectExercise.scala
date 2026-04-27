package interactionPlugins.turtleStitchPlugin

import datastructures.web.file.FileDescription
import interactionPlugins.turtleStitchPlugin.card.TurtleFileShowProgramXmlCard
import workbook.htmlElements.basic.*
import workbook.htmlElements.interactions.HtmlBasicTextInteraction
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo

import scala.concurrent.ExecutionContext

object TurtleStitchExploreProjectExercise {

  def createElementLine(workbookInfo: FullInfo, projectToDownload: FileDescription): HtmlWorkbookElement =
    TurtleFileShowProgramXmlCard(workbookInfo, projectToDownload).asWorkbookElement

}
