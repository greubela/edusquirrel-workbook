package it.evadid.workbook.model.interaction.plugins.gpt

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.basic.MessagingInteraction

case class GptInteractionElement(
                                  id: String,
                                  underlyingTextInteraction: WorkbookInteraction[String],
                                  exerciseText: LanguageMapContentId,
                                  scaffoldingHints: List[LanguageMapContentId],
                                  gradingCriteria: List[LanguageMapContentId]
                                ) extends WorkbookElement {

  val scaffoldingInteractionOp: Option[MessagingInteraction] = {
    if(scaffoldingHints.nonEmpty){
      println("[WARN] creating messaging interaction for id '" + id + "' with test scaffolding hints")
      Some(MessagingInteraction(id + "_scaffoldingMessenger", MessengerModel.testCompletion))
    }else{
      None
    }
  }

  override lazy val childrenOfThisElement: List[WorkbookElement] = scaffoldingInteractionOp.toList

  //private var htmlGptGrader = HtmlGptGrader(fullInfo, textInteraction)

  //val htmlGPTMessenger: Option[HtmlGPTMessenger] = languageMapIdScaffoldingHints.map(hintID => HtmlGPTMessenger(fullInfo, textInteraction, languageMapIDExerciseText, hintID))


}


