package it.evadid.workbook.model.interaction.plugins.sortingExercise

import it.evadid.core.util.io.Serializer
import upickle.default.{ReadWriter, macroRW}

enum AssignmentResult {
  case Correct
  case Wrong
}

/** None = unassigned; Some(fieldIndex) = correctly placed and locked in that field. */
case class SortingInteractionState(
  placedFieldIndexByItem: List[Option[Int]],
  sessionErrorCount: Int = 0,
  lastSessionErrorCount: Int = 0,
  /** Temporary wrong drop shown in a field before the item returns to the source list. */
  wrongPlacementPreview: Option[(Int, Int)] = None
) {

  def sanitized(itemCount: Int, fieldCount: Int): SortingInteractionState = {
    val padded = placedFieldIndexByItem.take(itemCount).padTo(itemCount, None)
    copy(
      placedFieldIndexByItem = padded.map(_.filter(index => index >= 0 && index < fieldCount)),
      sessionErrorCount = sessionErrorCount.max(0),
      lastSessionErrorCount = lastSessionErrorCount.max(0)
    )
  }

  def activeItemIndex(itemCount: Int, fieldCount: Int): Option[Int] =
    sanitized(itemCount, fieldCount).placedFieldIndexByItem.zipWithIndex.collectFirst {
      case (None, index) => index
    }

  def isComplete(itemCount: Int, fieldCount: Int): Boolean =
    sanitized(itemCount, fieldCount).placedFieldIndexByItem.forall(_.nonEmpty)

  def finalizeAttempt(): SortingInteractionState =
    copy(
      lastSessionErrorCount = sessionErrorCount.max(0),
      sessionErrorCount = 0,
      wrongPlacementPreview = None
    )

  def resetAttempt(itemCount: Int): SortingInteractionState =
    copy(
      placedFieldIndexByItem = List.fill(itemCount)(None),
      sessionErrorCount = 0,
      wrongPlacementPreview = None
    )

  def recordWrongAttempt(): SortingInteractionState =
    copy(sessionErrorCount = sessionErrorCount + 1)

  def recordWrongPlacement(itemIndex: Int, fieldIndex: Int): SortingInteractionState =
    recordWrongAttempt().copy(wrongPlacementPreview = Some((itemIndex, fieldIndex)))

  def clearWrongPlacementPreview(): SortingInteractionState =
    copy(wrongPlacementPreview = None)

  def tryAssign(
    itemIndex: Int,
    fieldIndex: Int,
    itemCount: Int,
    fieldCount: Int,
    correctFieldIndex: Int
  ): (SortingInteractionState, AssignmentResult) = {
    val clean = sanitized(itemCount, fieldCount)
    if itemIndex < 0 || itemIndex >= itemCount || fieldIndex < 0 || fieldIndex >= fieldCount then
      (clean, AssignmentResult.Wrong)
    else if clean.activeItemIndex(itemCount, fieldCount).contains(itemIndex) then
      if fieldIndex == correctFieldIndex then
        (
          clean.copy(placedFieldIndexByItem = clean.placedFieldIndexByItem.updated(itemIndex, Some(fieldIndex))),
          AssignmentResult.Correct
        )
      else
        (clean, AssignmentResult.Wrong)
    else
      (clean, AssignmentResult.Wrong)
  }
}

object SortingInteractionState {
  def initial(itemCount: Int): SortingInteractionState =
    SortingInteractionState(List.fill(itemCount)(None))

  private given ReadWriter[SortingInteractionState] = macroRW
  val serializer: Serializer[SortingInteractionState] = Serializer.fromUpickleJson(summon[ReadWriter[SortingInteractionState]])
}
