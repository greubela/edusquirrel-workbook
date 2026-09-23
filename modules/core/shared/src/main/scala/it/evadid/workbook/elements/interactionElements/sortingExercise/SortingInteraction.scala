package it.evadid.workbook.elements.interactionElements.sortingExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory

case class SortingInteraction(
                               override val elementId: String,
                               fields: List[LanguageMapContentId],
                               items: List[SortingItem],
                               openButtonLabel: LanguageMapContentId = LanguageMapContentId("basic/startSortingActivity")
) extends WorkbookInteractionElement[SortingInteractionState] {

  override val defaultValue: SortingInteractionState =
    SortingInteractionState.initial(items.size)

  override val serializerInteractionContent: Serializer[SortingInteractionState] = SortingInteractionState.serializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override def toSerializableType: WorkbookElementFactory = ???
}

case class SortingItem(
  label: LanguageMapContentId,
  correctFieldIndex: Int,
  wrongFeedback: LanguageMapContentId
)
