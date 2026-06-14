package it.evadid.workbook.model.interaction.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction

case class CategorizationInteraction(
                                      override val id: String,
                                      items: List[LanguageMapContentId],
                                      categories: List[LanguageMapContentId]
                                    ) extends WorkbookInteraction[CategorizationInteractionState] {
  override val defaultValue: CategorizationInteractionState = CategorizationInteractionState(List.fill(items.size)(None))
  override val serializer: Serializer[CategorizationInteractionState] = CategorizationInteractionState.serializer
}
