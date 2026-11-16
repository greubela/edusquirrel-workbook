package it.evadid.workbook.elements.interactionElements.sorting.sortingExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{InteractionFormat, DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.default.*

case class SortingInteraction(override val elementId: String, fields: List[LanguageMapContentId], items: List[SortingItem], openButtonLabel: LanguageMapContentId = LanguageMapContentId("basic/startSortingActivity")) extends WorkbookInteractionElement[SortingInteractionState] {

  override val associatedFactory = SortingInteraction.factory
  override val defaultValue = SortingInteractionState.initial(items.size);
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override val serializerInteractionContent: Serializer[SortingInteractionState] = Serializer.fromUpickleJson(SortingInteractionState.derived$ReadWriter)
  override def format: InteractionFormat[SortingInteractionState, Unit, Unit] = SortingInteraction.format
}

case class SortingItem(label: LanguageMapContentId, correctFieldIndex: Int, wrongFeedback: LanguageMapContentId) derives ReadWriter

object SortingInteraction extends InteractionFormat[SortingInteractionState, Unit, Unit] {

  val factory = new SimpleWorkbookElementFactory[SortingInteraction]() {
    override protected val constructorFieldOrder = List("elementId", "fields", "items", "openButtonLabel")
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

  override def stateSerializer: Serializer[SortingInteractionState] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[SortingInteractionState, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[SortingInteractionState, Unit, Unit]] = Serializer.fromImplicitRW

}
