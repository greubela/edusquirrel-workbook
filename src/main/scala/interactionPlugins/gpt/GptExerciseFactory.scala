package interactionPlugins.gpt

import com.raquo.laminar.api.L.Var
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.WorkbookInfo
import workbook.htmlElements.basic.*
import workbook.htmlElements.interactions.HtmlBasicTextInteraction

object GptExerciseFactory {


  private def expandInstruction(workbookInfo: Var[WorkbookInfo], myId: String, instruction: LanguageMap[HumanLanguage], withSpacerBefore: Boolean = false): List[HtmlWorkbookElement] = {

    val instr = HtmlPlaintextInstructionElement(workbookInfo, instruction)
    val text = HtmlBasicTextInteraction(workbookInfo, myId)
    val gpt = GptButtonLine(workbookInfo, text)
   // val spacer = HtmlSubExerciseSpacer(workbookInfo)
    if (withSpacerBefore) List(instr, text, gpt)
    else List(instr, text, gpt)
  }

  def createGptExercise(workbookInfo: Var[WorkbookInfo], baseId: String, title: LanguageMap[HumanLanguage], instruction: LanguageMap[HumanLanguage]): List[HtmlWorkbookElement] =
    createGptExercise(workbookInfo, baseId, title, List(instruction))

  def createGptExercise(workbookInfo: Var[WorkbookInfo], baseId: String, title: LanguageMap[HumanLanguage], instructions: List[LanguageMap[HumanLanguage]]): List[HtmlWorkbookElement] = {

    val htmlTitleElement = HtmlContainerTitle(workbookInfo, title)

    val expandedInstructions = instructions.zipWithIndex.flatMap(tup => expandInstruction(workbookInfo, baseId + "_" + tup._2, tup._1, false))

    List(htmlTitleElement) ++ expandedInstructions
  }


}
