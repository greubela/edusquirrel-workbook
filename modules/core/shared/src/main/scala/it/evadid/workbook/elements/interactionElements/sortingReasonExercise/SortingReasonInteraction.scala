package it.evadid.workbook.elements.interactionElements.sortingReasonExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory

case class SortingReasonInteraction(
                                     override val elementId: String,
                                     fields: List[LanguageMapContentId],
                                     items: List[SortingReasonItem],
                                     openButtonLabel: LanguageMapContentId = LanguageMapContentId("basic/startSortingReasonActivity")
) extends WorkbookInteractionElement[SortingReasonInteractionState] {

  override val defaultValue: SortingReasonInteractionState =
    SortingReasonInteractionState.initial(items.size)

  override val serializerInteractionContent: Serializer[SortingReasonInteractionState] = SortingReasonInteractionState.serializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override def toSerializableType: WorkbookElementFactory = ???
}

case class SortingReasonItem(
  label: LanguageMapContentId,
  correctFieldIndex: Int,
  wrongFeedback: LanguageMapContentId,
  reasonPrompt: LanguageMapContentId
)
