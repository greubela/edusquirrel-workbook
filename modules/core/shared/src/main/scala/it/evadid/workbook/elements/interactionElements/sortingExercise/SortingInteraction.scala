package it.evadid.workbook.elements.interactionElements.sortingExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.default.{ReadWriter, macroRW}

case class SortingInteraction(override val elementId: String, fields: List[LanguageMapContentId], items: List[SortingItem], openButtonLabel: LanguageMapContentId = LanguageMapContentId("basic/startSortingActivity")) extends WorkbookInteractionElement[SortingInteractionState] {
  override val defaultValue = SortingInteractionState.initial(items.size);
  override val serializerInteractionContent = SortingInteractionState.serializer;
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

 }

case class SortingItem(label: LanguageMapContentId, correctFieldIndex: Int, wrongFeedback: LanguageMapContentId)

object SortingInteraction {
  private given contentIdRW: ReadWriter[LanguageMapContentId] = LanguageMapContentId.serializer.uPickleReadWrite;

  private given itemRW: ReadWriter[SortingItem] = macroRW;
  private[sortingExercise] val contentIds = Serializer.fromUpickleJson(summon[ReadWriter[List[LanguageMapContentId]]]);
  private[sortingExercise] val itemsSerializer = Serializer.fromUpickleJson(summon[ReadWriter[List[SortingItem]]]);

  }
