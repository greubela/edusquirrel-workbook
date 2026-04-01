package interactionPlugins.gpt

import com.raquo.laminar.api.L.Var
import datastructures.core.language.{HumanLanguage, LanguageMap}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}
import workbook.htmlElements.basic.*
import workbook.htmlElements.interactions.HtmlBasicTextInteraction

object GptExerciseFactory {


  private def expandInstruction(workbookInfo: AllWorkbookInfo, myId: String, instruction: LanguageMap[HumanLanguage], withSpacerBefore: Boolean = false): List[HtmlWorkbookElement] = {

    val instr = HtmlPlaintextInstructionElement(workbookInfo, workbookInfo.stringSignalFromLanguageMap(instruction))
    val text = HtmlBasicTextInteraction(workbookInfo, myId)
    val gpt = GptButtonLine(workbookInfo, text)
   // val spacer = HtmlSubExerciseSpacer(workbookInfo)
    if (withSpacerBefore) List(instr, text, gpt)
    else List(instr, text, gpt)
  }

  def createGptExercise(workbookInfo: AllWorkbookInfo, baseId: String, title: LanguageMap[HumanLanguage], instruction: LanguageMap[HumanLanguage]): List[HtmlWorkbookElement] =
    createGptExercise(workbookInfo, baseId, title, List(instruction))

  def createGptExercise(workbookInfo: AllWorkbookInfo, baseId: String, title: LanguageMap[HumanLanguage], instructions: List[LanguageMap[HumanLanguage]]): List[HtmlWorkbookElement] = {

    val htmlTitleElement = HtmlContainerTitle(workbookInfo, workbookInfo.stringSignalFromLanguageMap(title))

    val expandedInstructions = instructions.zipWithIndex.flatMap(tup => expandInstruction(workbookInfo, baseId + "_" + tup._2, tup._1, false))

    List(htmlTitleElement) ++ expandedInstructions
  }


}
