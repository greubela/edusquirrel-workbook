package it.evadid.workbook.model.interaction.plugins.sortingReasonExercise

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.plugins.sortingExercise.AssignmentResult
import upickle.default.{ReadWriter, macroRW}

case class SortingReasonInteractionState(
  placedFieldIndexByItem: List[Option[Int]],
  reasonsByItem: List[String],
  reasonConfirmedByItem: List[Boolean],
  sessionErrorCount: Int = 0,
  lastSessionErrorCount: Int = 0,
  /** Temporary wrong drop shown in a field before the item returns to the source list. */
  wrongPlacementPreview: Option[(Int, Int)] = None
) {

  def sanitized(itemCount: Int, fieldCount: Int): SortingReasonInteractionState = {
    val paddedPlacements = placedFieldIndexByItem.take(itemCount).padTo(itemCount, None)
    val paddedReasons = reasonsByItem.take(itemCount).padTo(itemCount, "")
    val paddedConfirmed = reasonConfirmedByItem.take(itemCount).padTo(itemCount, false)
    copy(
      placedFieldIndexByItem = paddedPlacements.map(_.filter(index => index >= 0 && index < fieldCount)),
      reasonsByItem = paddedReasons,
      reasonConfirmedByItem = paddedConfirmed,
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

  def isReasonConfirmed(itemIndex: Int, itemCount: Int, fieldCount: Int): Boolean =
    sanitized(itemCount, fieldCount).reasonConfirmedByItem.lift(itemIndex).contains(true)

  def canDragActiveItem(itemCount: Int, fieldCount: Int): Boolean =
    activeItemIndex(itemCount, fieldCount).exists(isReasonConfirmed(_, itemCount, fieldCount))

  def finalizeAttempt(): SortingReasonInteractionState =
    copy(
      lastSessionErrorCount = sessionErrorCount.max(0),
      sessionErrorCount = 0,
      wrongPlacementPreview = None
    )

  def resetAttempt(itemCount: Int): SortingReasonInteractionState =
    copy(
      placedFieldIndexByItem = List.fill(itemCount)(None),
      reasonsByItem = List.fill(itemCount)(""),
      reasonConfirmedByItem = List.fill(itemCount)(false),
      sessionErrorCount = 0,
      wrongPlacementPreview = None
    )

  def recordWrongAttempt(): SortingReasonInteractionState =
    copy(sessionErrorCount = sessionErrorCount + 1)

  def recordWrongPlacement(itemIndex: Int, fieldIndex: Int): SortingReasonInteractionState =
    recordWrongAttempt().copy(wrongPlacementPreview = Some((itemIndex, fieldIndex)))

  def clearWrongPlacementPreview(): SortingReasonInteractionState =
    copy(wrongPlacementPreview = None)

  def withReasonDraft(itemIndex: Int, reason: String, itemCount: Int, fieldCount: Int): SortingReasonInteractionState = {
    val clean = sanitized(itemCount, fieldCount)
    if itemIndex < 0 || itemIndex >= itemCount then clean
    else clean.copy(reasonsByItem = clean.reasonsByItem.updated(itemIndex, reason))
  }

  def confirmReason(itemIndex: Int, itemCount: Int, fieldCount: Int): SortingReasonInteractionState = {
    val clean = sanitized(itemCount, fieldCount)
    if itemIndex < 0 || itemIndex >= itemCount then clean
    else if clean.reasonsByItem(itemIndex).trim.isEmpty then clean
    else clean.copy(reasonConfirmedByItem = clean.reasonConfirmedByItem.updated(itemIndex, true))
  }

  def tryAssign(
    itemIndex: Int,
    fieldIndex: Int,
    itemCount: Int,
    fieldCount: Int,
    correctFieldIndex: Int
  ): (SortingReasonInteractionState, AssignmentResult) = {
    val clean = sanitized(itemCount, fieldCount)
    if itemIndex < 0 || itemIndex >= itemCount || fieldIndex < 0 || fieldIndex >= fieldCount then
      (clean, AssignmentResult.Wrong)
    else if !clean.isReasonConfirmed(itemIndex, itemCount, fieldCount) then
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

object SortingReasonInteractionState {
  def initial(itemCount: Int): SortingReasonInteractionState =
    SortingReasonInteractionState(
      List.fill(itemCount)(None),
      List.fill(itemCount)(""),
      List.fill(itemCount)(false)
    )

  private given ReadWriter[SortingReasonInteractionState] = macroRW
  val serializer: Serializer[SortingReasonInteractionState] =
    Serializer.fromUpickleJson(summon[ReadWriter[SortingReasonInteractionState]])
}
