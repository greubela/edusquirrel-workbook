package it.evadid.workbook.elements.interactionElements.sortingReasonExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.default.*

case class SortingReasonInteraction(
                                     override val elementId: String,
                                     fields: List[LanguageMapContentId],
                                     items: List[SortingReasonItem],
                                     openButtonLabel: LanguageMapContentId = LanguageMapContentId("basic/startSortingReasonActivity")
                                   ) extends WorkbookInteractionElement[SortingReasonInteractionState] {
  override val associatedFactory = SortingReasonInteraction.factory

  override val defaultValue: SortingReasonInteractionState =
    SortingReasonInteractionState.initial(items.size)

  override val serializerInteractionContent: Serializer[SortingReasonInteractionState] = SortingReasonInteractionState.serializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

}

case class SortingReasonItem(
                              label: LanguageMapContentId,
                              correctFieldIndex: Int,
                              wrongFeedback: LanguageMapContentId,
                              reasonPrompt: LanguageMapContentId
                            ) derives ReadWriter

object SortingReasonInteraction {
  private given contentIdRW: ReadWriter[LanguageMapContentId] = DefaultSerializer.serializerLangMapId.uPickleReadWrite;

    val factory = new SimpleWorkbookElementFactory[SortingReasonInteraction]() {
    override def finishSerialization(baseElement: WorkbookElementSerializable, e: SortingReasonInteraction): WorkbookElementSerializable = {
      baseElement
        .withElementsAddedAs[LanguageMapContentId]("fields", e.fields)
        .withElementsAddedAs[SortingReasonItem]("items", e.items)
        .withContentIdAdded("openButtonLabel", e.openButtonLabel)
    }

    override def finishDeserialization(f: WorkbookElementSerializable): SortingReasonInteraction = {
      SortingReasonInteraction(
        f.elementId,
        f.getElementsAs[LanguageMapContentId]("fields"),
        f.getElementsAs[SortingReasonItem]("items"),
        f.getElementAsContentId("openButtonLabel")
      )

    }
  }


}
