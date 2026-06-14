package it.evadid.workbook.model.interaction.basic

import it.evadid.core.datastructures.language.LanguageMapContentId

case class MultipleChoiceInteraction(
                                      override val id: String,
                                      override val options: List[LanguageMapContentId],
                                      override val prompt: Option[LanguageMapContentId] = None
                                    ) extends ChoiceSelectionInteraction {
  override val allowMultiple: Boolean = true
}
