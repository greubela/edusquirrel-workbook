package interactionPlugins.gpt

import workbook.htmlElements.basic.*
import workbook.htmlElements.interactions.HtmlBasicTextInteraction
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo

import scala.concurrent.ExecutionContext

object GptExerciseFactory {


  private def expandInstruction(workbookInfo: FullInfo, myId: String, instructionLanguageMapId: String, withSpacerBefore: Boolean = false): List[HtmlWorkbookElement] = {

    val instr = HtmlInstructionElement.fromPlaintextLanguageMapId(workbookInfo, instructionLanguageMapId)
    val text = HtmlBasicTextInteraction(workbookInfo, myId)
    val gpt = GptButtonLine(workbookInfo, text)
    if (withSpacerBefore) List(instr, text, gpt)
    else List(instr, text, gpt)
  }

  def createGptExercise(workbookInfo: FullInfo, baseId: String, titleLanguageMapId: String, instructionLanguageMapId: String): List[HtmlWorkbookElement] =
    createGptExercise(workbookInfo, baseId, titleLanguageMapId, List(instructionLanguageMapId))

  def createGptExercise(workbookInfo: FullInfo, baseId: String, titleLanguageMapId: String, instructionLanguageMapIds: List[String]): List[HtmlWorkbookElement] = {

    val htmlTitleElement = HtmlContainerTitle(workbookInfo, titleLanguageMapId)

    val expandedInstructions = instructionLanguageMapIds.zipWithIndex.flatMap(tup => expandInstruction(workbookInfo, baseId + "_" + tup._2, tup._1, false))

    List(htmlTitleElement) ++ expandedInstructions
  }


}
