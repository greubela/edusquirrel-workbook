package it.evadid.workbook.interactions.sorting.sortingExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.InteractionFormat.SortingInteractionFormat
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookElement, WorkbookInteractionElement, *}
import it.evadid.workbook.serialization.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.serialization.WorkbookElementSerializable
import it.evadid.workbook.history.InteractionHistory
import upickle.default.*

case class SortingInteraction(
                               override val elementId: String, fields: Seq[LanguageMapContentId], items: Seq[SortingItem],
                               openButtonLabel: LanguageMapContentId = LanguageMapContentId("basic/startSortingActivity")
                             ) extends WorkbookInteractionElement[SortingInteractionFormat.type] {

  override val associatedFactory = SortingInteraction.factory
  override val defaultValue = SortingInteractionState.initial(items.size);
  override lazy val childrenOfThisElement: Seq[WorkbookElement] = List()

}

case class SortingItem(label: LanguageMapContentId, correctFieldIndex: Int, wrongFeedback: LanguageMapContentId) derives ReadWriter

object SortingInteraction {


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
}
