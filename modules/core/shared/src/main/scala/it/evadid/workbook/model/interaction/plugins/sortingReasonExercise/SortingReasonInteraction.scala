package it.evadid.workbook.model.interaction.plugins.sortingReasonExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction

case class SortingReasonInteraction(
  override val id: String,
  fields: List[LanguageMapContentId],
  items: List[SortingReasonItem],
  openButtonLabel: LanguageMapContentId = LanguageMapContentId("basic/startSortingReasonActivity")
) extends WorkbookInteraction[SortingReasonInteractionState] {

  override val defaultValue: SortingReasonInteractionState =
    SortingReasonInteractionState.initial(items.size)

  override val serializer: Serializer[SortingReasonInteractionState] = SortingReasonInteractionState.serializer
}

case class SortingReasonItem(
  label: LanguageMapContentId,
  correctFieldIndex: Int,
  wrongFeedback: LanguageMapContentId,
  reasonPrompt: LanguageMapContentId
)
