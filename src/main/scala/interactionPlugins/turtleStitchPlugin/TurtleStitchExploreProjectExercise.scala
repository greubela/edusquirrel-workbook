package interactionPlugins.turtleStitchPlugin

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.file.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import interactionPlugins.turtleStitchPlugin.card.*
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.WorkbookInfo
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement}
import workbook.htmlElements.interactions.HtmlBasicTextInteraction

object TurtleStitchExploreProjectExercise {

  def createElementLine(workbookInfoVar: Var[WorkbookInfo], projectToDownload: FileDescription): HtmlWorkbookElement =
    TurtleFileShowProgramXmlCard(workbookInfoVar, projectToDownload).asWorkbookElement

  def createElements(
                      workbookInfo: Var[WorkbookInfo],
                      baseId: String,
                      title: LanguageMap[HumanLanguage],
                      imageShowCommands: FileDescription,
                      projectToDownload: FileDescription
                    ): List[HtmlWorkbookElement] = {

    val htmlTitleElement = HtmlContainerTitle(workbookInfo, title)
    val instr = HtmlPlaintextInstructionElement(workbookInfo, TurtleStitchLanguageMaps.languageMapDefaultReadExerciseInstruction)
    //val down = TurtleFileProgramPreviewCard(workbookInfo, baseId, projectToDownload)

    val preview: HtmlWorkbookElement = TurtleFileShowProgramXmlCard(
      workbookInfo,
      projectToDownload
    )


    val instr2 = HtmlPlaintextInstructionElement(workbookInfo, TurtleStitchLanguageMaps.languageMapDefaultAnalyzeExerciseInstruction)

    val text = HtmlBasicTextInteraction(workbookInfo, baseId)

    val instr3 = HtmlPlaintextInstructionElement(workbookInfo, TurtleStitchLanguageMaps.languageMapDefaultExecuteExerciseInstruction)

    List(htmlTitleElement, instr, preview, instr2, text, instr3)

  }

}
