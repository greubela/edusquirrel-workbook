package it.evadid.workbook.elements.interactionElements.sortingExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.default.{ReadWriter, macroRW}

case class SortingInteraction(override val elementId: String, fields: List[LanguageMapContentId], items: List[SortingItem], openButtonLabel: LanguageMapContentId = LanguageMapContentId("basic/startSortingActivity")) extends WorkbookInteractionElement[SortingInteractionState] {
  override val associatedFactory = SortingInteraction.factory
  override val defaultValue = SortingInteractionState.initial(items.size);
  override val serializerInteractionContent = SortingInteractionState.serializer;
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
}

case class SortingItem(label: LanguageMapContentId, correctFieldIndex: Int, wrongFeedback: LanguageMapContentId)

object SortingInteraction {
  private given contentIdRW: ReadWriter[LanguageMapContentId] = DefaultSerializer.serializerLangMapId.uPickleReadWrite;

  private given itemRW: ReadWriter[SortingItem] = macroRW;
  private[sortingExercise] val contentIds = Serializer.fromUpickleJson(summon[ReadWriter[List[LanguageMapContentId]]]);
  private[sortingExercise] val itemsSerializer = Serializer.fromUpickleJson(summon[ReadWriter[List[SortingItem]]]);
  val factory = new SimpleWorkbookElementFactory[SortingInteraction](){
    override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: SortingInteraction): WorkbookElementSerializable = {
      baseElement
        .withElementAdded("fields", infoElement.fields)(contentIds)
        .withElementAdded("items", infoElement.items)(itemsSerializer)
        .withContentIdAdded("openButtonLabel", infoElement.openButtonLabel)
    }

    override def finishDeserialization(f: WorkbookElementSerializable): SortingInteraction = {
      SortingInteraction(f.elementId, f.getElementAs("fields")(contentIds), f.getElementAs("items")(itemsSerializer), f.getElementAsContentId("openButtonLabel"))

    }
  }

}
