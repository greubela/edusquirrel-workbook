package it.evadid.workbook.elements.interactionElements.reorderExercise

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import ReorderInteractionState.ReorderInteractionStateSerialized
import ReorderType.CODELINES
import it.evadid.workbook.model.interaction.variable.{InteractionVariableState, InteractionVariableStateSerialized}
import upickle.{ReadWriter, macroRW}

import java.lang.annotation.ElementType
import scala.util.Random

/**
 * Captures the model state for an interaction where a learner reorders a fixed list of elements.
 * The state owns all order-validation and order-update rules so renderers can focus on collecting UI gestures and displaying the current order.
 */
case class ReorderInteractionState[T](
                                       elements: List[T],
                                       currentOrder: List[Int],
                                       correctOrder: List[Int],
                                       elementType: ReorderType,
                                       elementSerializer: Serializer[T]
                                     ) {

  assert(elements.size == correctOrder.size, "elements and correct order must have the same size!")
  assert(elements.size == currentOrder.size, "elements and current order must have the same size!")
  assert(elements.indices.forall(curIndex => currentOrder.contains(curIndex)), "all indices from elements must appear in current order!")
  assert(elements.indices.forall(curIndex => correctOrder.contains(curIndex)), "all indices from elements must appear in correct order!")

  /**
   * Returns the canonical list of valid element indices for this reorder interaction.
   * This is the fallback order when deserialized or externally supplied state contains missing, duplicate, or unknown indices.
   */
  lazy val validElementOrder: List[Int] = elements.indices.toList

  /**
   * Validates an order against this state's elements and falls back to the canonical order when needed.
   * Keeping this in core ensures every renderer and future evaluator shares the same definition of a valid order.
   */
  def sanitizeOrder(order: List[Int]): List[Int] = {
    if (order.length == elements.length && order.toSet == validElementOrder.toSet) order else validElementOrder
  }

  /**
   * Returns the currently displayed order after applying the shared validation rules.
   * Renderers should prefer this method over reading `currentOrder` directly when producing UI from persisted state.
   */
  lazy val sanitizedCurrentOrder: List[Int] = sanitizeOrder(currentOrder)

  /**
   * Moves an element index to a new insertion point and returns an updated state.
   * The method mirrors drag-and-drop insertion semantics while preserving the immutable model state and clamping out-of-range insertion points.
   */
  def moveElementToIndex(draggedElementIndex: Int, insertIndex: Int): ReorderInteractionState[T] = {
    val fromIndex = sanitizedCurrentOrder.indexOf(draggedElementIndex)
    if (fromIndex < 0) this
    else {
      val clean = sanitizedCurrentOrder.filterNot(_ == draggedElementIndex)
      val adjustedInsertIndex = if (insertIndex > fromIndex) insertIndex - 1 else insertIndex
      val safeIndex = adjustedInsertIndex.max(0).min(clean.length)
      val (front, back) = clean.splitAt(safeIndex)
      copy(currentOrder = front ++ List(draggedElementIndex) ++ back)
    }
  }

  lazy val toSerialized: ReorderInteractionStateSerialized = ReorderInteractionStateSerialized(elements.map(elementSerializer.serialize), currentOrder, correctOrder, elementType)

  lazy val serializer: Serializer[ReorderInteractionState[T]] = new Serializer[ReorderInteractionState[T]] {

    override def serialize(obj: ReorderInteractionState[T]): String = {
      ReorderInteractionState.serializer.serialize(obj.toSerialized)
    }

    override def deserialize(str: String): ReorderInteractionState[T] = {
      val serializedState = ReorderInteractionState.serializer.deserialize(str)
      ReorderInteractionState(elementSerializer, serializedState)
    }
  }
}

object ReorderInteractionState {

  def initStateFromElementsAndSeed[T](elementsInCorrectOrder: List[T], seed: Long, elementSerializer: Serializer[T], reorderType: ReorderType): ReorderInteractionState[T] = {
    val shuffled: List[Int] = Random(seed).shuffle(elementsInCorrectOrder.indices.toList)
    ReorderInteractionState[T](elementsInCorrectOrder, shuffled, elementsInCorrectOrder.indices.toList, reorderType, elementSerializer)
  }

  /*

                                       elements: List[T],
                                       currentOrder: List[Int],
                                       correctOrder: List[Int],
                                       elementType: ReorderType,
                                       elementSerializer: Serializer[T]
   */
  case class ReorderInteractionStateSerialized(elements: List[String], currentOrder: List[Int], correctOrder: List[Int], elementType: ReorderType) {
    def toTyped[T](serializer: Serializer[T]): ReorderInteractionState[T] = ReorderInteractionState(serializer, this)
  }

  def apply[T](elementSerializer: Serializer[T], serializedInteraction: ReorderInteractionStateSerialized): ReorderInteractionState[T] = {
    val elements = serializedInteraction.elements.map(elementSerializer.deserialize)
    ReorderInteractionState[T](elements, serializedInteraction.currentOrder, serializedInteraction.correctOrder, serializedInteraction.elementType, elementSerializer)
  }

  def forCode(lines: List[String], currentOrder: List[Int], correctOrder: List[Int], codelineType: CODELINES): ReorderInteractionState[String] = {
    ReorderInteractionState[String](lines, currentOrder, correctOrder, codelineType, Serializer.stringIO)
  }

  def forIds(ids: List[LanguageMapContentId], currentOrder: List[Int], correctOrder: List[Int]): ReorderInteractionState[LanguageMapContentId] = {
    //val ids = ids.map(LanguageMapContentId.apply)
    ReorderInteractionState[LanguageMapContentId](ids, currentOrder, correctOrder, ReorderType.LANGUAGE_MAP_IDS, LanguageMapContentId.serializer)
  }

  private[reorderExercise] given lmci: ReadWriter[LanguageMapContentId] = LanguageMapContentId.serializer.uPickleReadWrite

  private[reorderExercise] given et: ReadWriter[ReorderType] = ReorderType.serializer.uPickleReadWrite

  private[reorderExercise] given ris: ReadWriter[ReorderInteractionStateSerialized] = macroRW

  private[reorderExercise] val serializer: Serializer[ReorderInteractionStateSerialized] = Serializer.fromUpickleJson(ris)

}
