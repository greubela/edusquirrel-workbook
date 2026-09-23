package it.evadid.workbook.elements.interactionElements.sortingReasonExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import upickle.default.{ReadWriter, macroRW}

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

  override def toSerializableType: WorkbookElementFactory = toFactoryBase.withElementAdded("fields", fields)(SortingReasonInteraction.contentIds).withElementAdded("items", items)(SortingReasonInteraction.itemsSerializer).withContentIdAdded("openButtonLabel", openButtonLabel)
}

case class SortingReasonItem(
  label: LanguageMapContentId,
  correctFieldIndex: Int,
  wrongFeedback: LanguageMapContentId,
  reasonPrompt: LanguageMapContentId
)

object SortingReasonInteraction {
 private given contentIdRW: ReadWriter[LanguageMapContentId] = LanguageMapContentId.serializer.uPickleReadWrite
 private given itemRW: ReadWriter[SortingReasonItem] = macroRW
 private[sortingReasonExercise] val contentIds = Serializer.fromUpickleJson(summon[ReadWriter[List[LanguageMapContentId]]])
 private[sortingReasonExercise] val itemsSerializer = Serializer.fromUpickleJson(summon[ReadWriter[List[SortingReasonItem]]])
 def fromFactory(f: WorkbookElementFactory): SortingReasonInteraction = SortingReasonInteraction(f.elementId, f.getElementAs("fields")(contentIds), f.getElementAs("items")(itemsSerializer), f.getElementAsContentId("openButtonLabel"))
}
