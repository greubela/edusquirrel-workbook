package interactionPlugins.gpt

import com.raquo.laminar.api.L.Var
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.basic.{HtmlBasicExerciseTitle, HtmlPlaintextInstructionElement}
import workbook.workbookHtmlElements.interactions.HtmlBasicTextInteraction

object GptExerciseFactory {


  private def expandInstruction(workbookInfo: Var[WorkbookInfo], myId: String, instruction: LanguageMap[HumanLanguage], withEmptyTitleBefore: Boolean = false): List[HtmlWorkbookElement] = {

    val instr = HtmlPlaintextInstructionElement(workbookInfo, instruction)
    val text = HtmlBasicTextInteraction(workbookInfo, myId)
    val gpt = GptButtonLine(workbookInfo, text)
    val emptyTitle = HtmlBasicExerciseTitle(workbookInfo, LanguageMap.universalMap(""))
    if (withEmptyTitleBefore) List(emptyTitle, instr, text, gpt)
    else List(instr, text, gpt)
  }

  def createGptExercise(workbookInfo: Var[WorkbookInfo], baseId: String, title: LanguageMap[HumanLanguage], instruction: LanguageMap[HumanLanguage]): List[HtmlWorkbookElement] =
    createGptExercise(workbookInfo, baseId, title, List(instruction))

  def createGptExercise(workbookInfo: Var[WorkbookInfo], baseId: String, title: LanguageMap[HumanLanguage], instructions: List[LanguageMap[HumanLanguage]]): List[HtmlWorkbookElement] = {

    val htmlTitleElement = HtmlBasicExerciseTitle(workbookInfo, title)

    val expandedInstructions = instructions.zipWithIndex.flatMap(tup => expandInstruction(workbookInfo, baseId + "_" + tup._2, tup._1, tup._2 != 0))

    List(htmlTitleElement) ++ expandedInstructions
  }


}
