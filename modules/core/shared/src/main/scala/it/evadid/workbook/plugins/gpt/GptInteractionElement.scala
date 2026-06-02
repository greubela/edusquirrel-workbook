package it.evadid.workbook.plugins.gpt

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


  private val defaultScaffolding: MessengerModel = ???
  private val scaffoldingMessenger: MessagingInteraction = MessagingInteraction("scaffoldingMessenger", defaultScaffolding)

  override lazy val childrenOfThisElement: List[WorkbookElement] = List(scaffoldingMessenger)

  //private var htmlGptGrader = HtmlGptGrader(fullInfo, textInteraction)

  //val htmlGPTMessenger: Option[HtmlGPTMessenger] = languageMapIdScaffoldingHints.map(hintID => HtmlGPTMessenger(fullInfo, textInteraction, languageMapIDExerciseText, hintID))


}


