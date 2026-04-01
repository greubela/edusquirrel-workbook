package interactionPlugins.turtleStitchPlugin

import datastructures.web.file.FileDescription
import interactionPlugins.turtleStitchPlugin.card.TurtleFileShowProgramXmlCard
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement}
import workbook.htmlElements.interactions.HtmlBasicTextInteraction
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

import scala.concurrent.ExecutionContext

object TurtleStitchExploreProjectExercise {

  def createElementLine(workbookInfo: AllWorkbookInfo, projectToDownload: FileDescription): HtmlWorkbookElement =
    TurtleFileShowProgramXmlCard(workbookInfo, projectToDownload).asWorkbookElement

  def createElements(
                      workbookInfo: AllWorkbookInfo,
                      baseId: String,
                      titleLanguageMapId: String,
                      imageShowCommands: FileDescription,
                      projectToDownload: FileDescription
                    ): List[HtmlWorkbookElement] = {

    val htmlTitleElement = HtmlContainerTitle(workbookInfo, titleLanguageMapId)
    val instr = HtmlPlaintextInstructionElement(workbookInfo, workbookInfo.stringSignalFromLanguageMapId("TurtleStitch/defaultReadExerciseInstruction")(ExecutionContext.global))

    val preview: HtmlWorkbookElement = TurtleFileShowProgramXmlCard(
      workbookInfo,
      projectToDownload
    )

    val instr2 = HtmlPlaintextInstructionElement(workbookInfo, workbookInfo.stringSignalFromLanguageMapId("TurtleStitch/defaultAnalyzeExerciseInstruction")(ExecutionContext.global))

    val text = HtmlBasicTextInteraction(workbookInfo, baseId)

    val instr3 = HtmlPlaintextInstructionElement(workbookInfo, workbookInfo.stringSignalFromLanguageMapId("TurtleStitch/defaultExecuteExerciseInstruction")(ExecutionContext.global))

    List(htmlTitleElement, instr, preview, instr2, text, instr3)

  }

}
