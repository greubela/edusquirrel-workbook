package it.evadid.workbook.model.interaction.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction

case class MatchingInteraction(
                                override val id: String,
                                leftItems: List[LanguageMapContentId],
                                rightItems: List[LanguageMapContentId]
                              ) extends WorkbookInteraction[MatchingInteractionState] {
  override val defaultValue: MatchingInteractionState = MatchingInteractionState(List.fill(leftItems.size)(None))
  override val serializer: Serializer[MatchingInteractionState] = MatchingInteractionState.serializer
}
