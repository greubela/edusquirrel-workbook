package interactionPlugins.turtleStitchPlugin

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.ImageDescription
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap, TranslationMaps}
import contentmanagement.storage.DataStorage
import interactionPlugins.fileSubmission.*
import interactionPlugins.turtleStitchPlugin.TurtleStitchFacade
import interactionPlugins.turtleStitchPlugin.card.*
import org.scalajs.dom.URL
import util.{HtmlHelper, ReadOnlyVar}
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement}
import workbook.workbookHtmlElements.interactions.HtmlBasicTextInteraction

import scala.concurrent.ExecutionContext

object TurtleStitchExploreProjectExercise {


  def createElementLine(workbookInfoVar: Var[WorkbookInfo], projectToDownload: URL): HtmlWorkbookElement = 
    TurtleFileShowProgramXmlCard(workbookInfoVar, projectToDownload).asWorkbookElement

  def createElements(
                      workbookInfo: Var[WorkbookInfo],
                      baseId: String,
                      title: LanguageMap[HumanLanguage],
                      imageShowCommands: ImageDescription,
                      projectToDownload: URL
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
