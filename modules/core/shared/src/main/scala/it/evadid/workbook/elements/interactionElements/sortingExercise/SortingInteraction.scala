package it.evadid.workbook.elements.interactionElements.sortingExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}

case class SortingInteraction(
  override val id: String,
  fields: List[LanguageMapContentId],
  items: List[SortingItem],
  openButtonLabel: LanguageMapContentId = LanguageMapContentId("basic/startSortingActivity")
) extends WorkbookInteractionElement[SortingInteractionState] {

  override val defaultValue: SortingInteractionState =
    SortingInteractionState.initial(items.size)

  override val serializer: Serializer[SortingInteractionState] = SortingInteractionState.serializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
}

case class SortingItem(
  label: LanguageMapContentId,
  correctFieldIndex: Int,
  wrongFeedback: LanguageMapContentId
)
