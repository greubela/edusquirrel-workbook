package interactionPlugins.turtleStitchPlugin
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.file.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap, TranslationMaps}
import contentmanagement.storage.DataStorage
import interactionPlugins.fileSubmission.*
import interactionPlugins.turtleStitchPlugin.TurtleStitchFacade
import interactionPlugins.turtleStitchPlugin.card.*
import interactionPlugins.turtleStitchPlugin.card.TurtleStitchFileUploadButtonCard.StorageFormat
import org.scalajs.dom.URL
import util.web.DownloadHelper
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.WorkbookInfo
import workbook.model.interaction.InteractionVariable
import workbook.model.interaction.history.UpdateImportance.TEMPORARY
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement}
import workbook.htmlElements.interactions.HtmlBasicTextInteraction

import scala.concurrent.ExecutionContext

object TurtleStitchRecreateShapeExercise {

  
  def createInteractionElement(
                                workbookInfo: Var[WorkbookInfo],
                                baseId: String,
                                expectedOutcome: FullImage
                              ): HtmlWorkbookElement = {
    new HtmlWorkbookElement() {

      private val fileInteraction = TurtleStitchFileUploadButtonCard(workbookInfoVar, baseId, List(".xml,text/xml"), StorageFormat.BYTES_AS_RAW_STRING)

      val preview: HtmlWorkbookElement = TurtleFileShowProgramXmlCard(fileInteraction)

      private val expectedOutcomePreview = TurtleStitchExpectedShapeCard(workbookInfoVar, expectedOutcome)

      override def workbookInfoVar: L.Var[WorkbookInfo] = workbookInfo

      override def getDomElement(): L.Element = div(
        cls := "workbook-interaction preview-line",
        fileInteraction.getDomElement(),
        preview.getDomElement(),
        expectedOutcomePreview.getDomElement()
      )
    }
  }

  def createElements(
                                    workbookInfoVar: Var[WorkbookInfo],
                                    baseId: String,
                                    title: LanguageMap[HumanLanguage],
                                    expectedOutcome: FullImage
                                  ): List[HtmlWorkbookElement] = {

    val htmlTitleElement = HtmlContainerTitle(workbookInfoVar, title)

    val instr = HtmlPlaintextInstructionElement(workbookInfoVar, TurtleStitchLanguageMaps.languageMapDefaultReprogramInstruction)

    val uploadLine: HtmlWorkbookElement = createInteractionElement(workbookInfoVar, baseId, expectedOutcome)

    List(htmlTitleElement, instr, uploadLine)
  }


}
