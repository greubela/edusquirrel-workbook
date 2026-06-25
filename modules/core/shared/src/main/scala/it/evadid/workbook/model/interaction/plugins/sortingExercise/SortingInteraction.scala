package it.evadid.workbook.model.interaction.plugins.sortingExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction

case class SortingInteraction(
  override val id: String,
  fields: List[LanguageMapContentId],
  items: List[SortingItem],
  openButtonLabel: LanguageMapContentId = LanguageMapContentId("basic/startSortingActivity")
) extends WorkbookInteraction[SortingInteractionState] {

  override val defaultValue: SortingInteractionState =
    SortingInteractionState.initial(items.size)

  override val serializer: Serializer[SortingInteractionState] = SortingInteractionState.serializer
}

case class SortingItem(
  label: LanguageMapContentId,
  correctFieldIndex: Int,
  wrongFeedback: LanguageMapContentId
)
