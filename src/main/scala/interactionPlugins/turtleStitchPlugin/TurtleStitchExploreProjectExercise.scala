package interactionPlugins.turtleStitchPlugin

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.core.language.{HumanLanguage, LanguageMap}
import datastructures.web.file.FileDescription
import interactionPlugins.turtleStitchPlugin.card.*
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement}
import workbook.htmlElements.interactions.HtmlBasicTextInteraction

object TurtleStitchExploreProjectExercise {

  def createElementLine(workbookInfo: AllWorkbookInfo, projectToDownload: FileDescription): HtmlWorkbookElement =
    TurtleFileShowProgramXmlCard(workbookInfo, projectToDownload).asWorkbookElement

  def createElements(
                      workbookInfo: AllWorkbookInfo,
                      baseId: String,
                      title: LanguageMap[HumanLanguage],
                      imageShowCommands: FileDescription,
                      projectToDownload: FileDescription
                    ): List[HtmlWorkbookElement] = {

    val htmlTitleElement = HtmlContainerTitle(workbookInfo, workbookInfo.stringSignalFromLanguageMap(title))
    val instr = HtmlPlaintextInstructionElement(workbookInfo, workbookInfo.stringSignalFromLanguageMap(TurtleStitchLanguageMaps.languageMapDefaultReadExerciseInstruction))
    //val down = TurtleFileProgramPreviewCard(workbookInfo, baseId, projectToDownload)

    val preview: HtmlWorkbookElement = TurtleFileShowProgramXmlCard(
      workbookInfo,
      projectToDownload
    )


    val instr2 = HtmlPlaintextInstructionElement(workbookInfo, workbookInfo.stringSignalFromLanguageMap(TurtleStitchLanguageMaps.languageMapDefaultAnalyzeExerciseInstruction))

    val text = HtmlBasicTextInteraction(workbookInfo, baseId)

    val instr3 = HtmlPlaintextInstructionElement(workbookInfo, workbookInfo.stringSignalFromLanguageMap(TurtleStitchLanguageMaps.languageMapDefaultExecuteExerciseInstruction))

    List(htmlTitleElement, instr, preview, instr2, text, instr3)

  }

}
