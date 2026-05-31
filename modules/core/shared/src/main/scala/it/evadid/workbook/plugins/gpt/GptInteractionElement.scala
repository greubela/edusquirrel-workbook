package it.evadid.workbook.plugins.gpt

import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.interaction.WorkbookInteraction

case class GptInteractionElement(
                               underlyingTextInteraction: WorkbookInteraction[String],
                               mapIdExerciseText: String,
                               mapIdScaffoldingHints: Option[String],
                               mapIdGradingHints: Option[String]
                             ) extends WorkbookElement {


  override lazy val childrenOfThisElement: List[WorkbookElement] = List() //List(htmlGPTMessenger).flatten

  //private var htmlGptGrader = HtmlGptGrader(fullInfo, textInteraction)

  //val htmlGPTMessenger: Option[HtmlGPTMessenger] = languageMapIdScaffoldingHints.map(hintID => HtmlGPTMessenger(fullInfo, textInteraction, languageMapIDExerciseText, hintID))


}


