package it.evadid.workbook.elements.interactionElements.sortingExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.default.*

case class SortingInteraction(override val elementId: String, fields: List[LanguageMapContentId], items: List[SortingItem], openButtonLabel: LanguageMapContentId = LanguageMapContentId("basic/startSortingActivity")) extends WorkbookInteractionElement[SortingInteractionState] derives ReadWriter {
  override val associatedFactory = SortingInteraction.factory
  override val defaultValue = SortingInteractionState.initial(items.size);
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override val serializerInteractionContent: Serializer[SortingInteractionState] = Serializer.fromUpickleJson(SortingInteractionState.derived$ReadWriter)
}

case class SortingItem(label: LanguageMapContentId, correctFieldIndex: Int, wrongFeedback: LanguageMapContentId) derives ReadWriter

object SortingInteraction {

  val factory = new SimpleWorkbookElementFactory[SortingInteraction]() {
    override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: SortingInteraction): WorkbookElementSerializable = {
      baseElement
        .withElementsAddedAs[LanguageMapContentId]("fields", infoElement.fields)
        .withElementsAddedAs("items", infoElement.items)
        .withElementAddedAs[LanguageMapContentId]("openButtonLabel", infoElement.openButtonLabel)
    }

    override def finishDeserialization(f: WorkbookElementSerializable): SortingInteraction = {
      SortingInteraction(
        f.elementId,
        f.getElementsAs[LanguageMapContentId]("fields"),
        f.getElementsAs[SortingItem]("items"),
        f.getElementAs[LanguageMapContentId]("openButtonLabel"))

    }
  }

}
