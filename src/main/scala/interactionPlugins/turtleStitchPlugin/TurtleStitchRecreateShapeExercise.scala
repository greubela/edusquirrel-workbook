package interactionPlugins.turtleStitchPlugin
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.core.language.{AppLanguage, HumanLanguage, LanguageMap, TranslationMaps}
import datastructures.web.file.FullImage
import datastructures.web.storage.AsyncDataCache
import interactionPlugins.fileSubmission.*
import interactionPlugins.turtleStitchPlugin.TurtleStitchFacade
import interactionPlugins.turtleStitchPlugin.card.*
import interactionPlugins.turtleStitchPlugin.card.TurtleStitchFileUploadButtonCard.StorageFormat
import org.scalajs.dom.URL
import util.web.DownloadHelper
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}
import workbook.model.interaction.InteractionVariable
import workbook.model.interaction.history.UpdateImportance.TEMPORARY
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement}
import workbook.htmlElements.interactions.HtmlBasicTextInteraction

import scala.concurrent.ExecutionContext

object TurtleStitchRecreateShapeExercise {

  
  def createInteractionElement(
                                pWorkbookInfo: AllWorkbookInfo,
                                baseId: String,
                                expectedOutcome: FullImage
                              ): HtmlWorkbookElement = {
    new HtmlWorkbookElement() {

      def workbookInfo: AllWorkbookInfo = pWorkbookInfo
      
      private val fileInteraction = TurtleStitchFileUploadButtonCard(pWorkbookInfo, baseId, List(".xml,text/xml"), StorageFormat.BYTES_AS_RAW_STRING)

      val preview: HtmlWorkbookElement = TurtleFileShowProgramXmlCard(fileInteraction)

      private val expectedOutcomePreview = TurtleStitchExpectedShapeCard(pWorkbookInfo, expectedOutcome)

      override def getDomElement(): L.Element = div(
        cls := "workbook-interaction preview-line",
        fileInteraction.getDomElement(),
        preview.getDomElement(),
        expectedOutcomePreview.getDomElement()
      )
    }
  }

  def createElements(
                      pWorkbookInfo: AllWorkbookInfo,
                                    baseId: String,
                                    title: LanguageMap[HumanLanguage],
                                    expectedOutcome: FullImage
                                  ): List[HtmlWorkbookElement] = {

    val htmlTitleElement = HtmlContainerTitle(pWorkbookInfo, pWorkbookInfo.stringSignalFromLanguageMap(title))

    val instr = HtmlPlaintextInstructionElement(pWorkbookInfo, pWorkbookInfo.stringSignalFromLanguageMap(TurtleStitchLanguageMaps.languageMapDefaultReprogramInstruction))

    val uploadLine: HtmlWorkbookElement = createInteractionElement(pWorkbookInfo, baseId, expectedOutcome)

    List(htmlTitleElement, instr, uploadLine)
  }


}
