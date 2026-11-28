package it.evadid.workbook.interactions.sorting.sortingReasonExercise

import it.evadid.workbook.interactions.sorting.sortingExercise.AssignmentResult
import munit.FunSuite

class SortingReasonInteractionStateSpec extends FunSuite {

  private val itemCount = 2
  private val fieldCount = 2

  test("assignment rejected when reason not confirmed") {
    val state = SortingReasonInteractionState.initial(itemCount)
      .withReasonDraft(0, "draft only", itemCount, fieldCount)

    val (_, result) = state.tryAssign(0, 0, itemCount, fieldCount, correctFieldIndex = 0)
    assertEquals(result, AssignmentResult.Wrong)
  }

  test("empty reason cannot be confirmed") {
    val state = SortingReasonInteractionState.initial(itemCount)
      .withReasonDraft(0, "   ", itemCount, fieldCount)
      .confirmReason(0, itemCount, fieldCount)

    assertEquals(state.isReasonConfirmed(0, itemCount, fieldCount), false)
  }

  test("serializer round-trips reason state") {
    val state = SortingReasonInteractionState(
      List(Some(1), None),
      List("weil jpeg verlustbehaftet ist", ""),
      List(true, false),
      sessionErrorCount = 1,
      lastSessionErrorCount = 3
    )

    val roundTrip = it.evadid.core.util.io.Serializer.fromImplicitRW[SortingReasonInteractionState].deserialize(
      it.evadid.core.util.io.Serializer.fromImplicitRW[SortingReasonInteractionState].serialize(state)
    )
    assertEquals(roundTrip, state)
  }

  private def reasoned(state: SortingReasonInteractionState, item: Int) =
    state.withReasonDraft(item, s"reason $item", itemCount, fieldCount).confirmReason(item, itemCount, fieldCount)

  test("items are sorted in order: only the first unplaced item with a confirmed reason can be dragged") {
    val start = SortingReasonInteractionState.initial(itemCount)
    assertEquals((start.activeItemIndex(itemCount, fieldCount), start.canDragActiveItem(itemCount, fieldCount)), (Some(0), false))
    val second = reasoned(start, 1)
    assert(!second.canDragActiveItem(itemCount, fieldCount), "the second item's reason does not unlock the first")
    assertEquals(second.tryAssign(1, 1, itemCount, fieldCount, correctFieldIndex = 1)._2, AssignmentResult.Wrong, "not its turn yet")
    val (placed, result) = reasoned(second, 0).tryAssign(0, 1, itemCount, fieldCount, correctFieldIndex = 1)
    assertEquals(result, AssignmentResult.Correct)
    assertEquals((placed.activeItemIndex(itemCount, fieldCount), placed.canDragActiveItem(itemCount, fieldCount)), (Some(1), true))
    val (done, _) = placed.tryAssign(1, 0, itemCount, fieldCount, correctFieldIndex = 0)
    assert(done.isComplete(itemCount, fieldCount))
    assertEquals(done.activeItemIndex(itemCount, fieldCount), None)
  }

  test("a wrong drop is counted and previewed until cleared; out-of-range targets are wrong without changes") {
    val ready = reasoned(SortingReasonInteractionState.initial(itemCount), 0)
    val (unchanged, wrong) = ready.tryAssign(0, 1, itemCount, fieldCount, correctFieldIndex = 0)
    assertEquals((wrong, unchanged), (AssignmentResult.Wrong, ready))
    val previewed = unchanged.recordWrongPlacement(0, 1)
    assertEquals((previewed.sessionErrorCount, previewed.wrongPlacementPreview), (1, Some(0 -> 1)))
    assertEquals(previewed.clearWrongPlacementPreview().wrongPlacementPreview, None)
    assertEquals(ready.tryAssign(0, 5, itemCount, fieldCount, 0)._2, AssignmentResult.Wrong)
    assertEquals(ready.tryAssign(-1, 0, itemCount, fieldCount, 0)._2, AssignmentResult.Wrong)
  }

  test("finishing an attempt keeps its error count; resetting clears answers but not the last result") {
    val attempt = reasoned(SortingReasonInteractionState.initial(itemCount), 0).recordWrongPlacement(0, 1).recordWrongAttempt()
    val finished = attempt.finalizeAttempt()
    assertEquals((finished.lastSessionErrorCount, finished.sessionErrorCount, finished.wrongPlacementPreview), (2, 0, None))
    val reset = finished.recordWrongAttempt().resetAttempt(itemCount)
    assertEquals(reset, SortingReasonInteractionState.initial(itemCount).copy(lastSessionErrorCount = 2))
  }

  test("a stored state with too few, too many or out-of-range entries is normalized to the exercise") {
    val stored = SortingReasonInteractionState(List(Some(7), Some(0), Some(1)), List("only one"), Nil, sessionErrorCount = -3, lastSessionErrorCount = -1)
    assertEquals(stored.sanitized(itemCount, fieldCount),
      SortingReasonInteractionState(List(None, Some(0)), List("only one", ""), List(false, false)))
    assertEquals(stored.activeItemIndex(itemCount, fieldCount), Some(0))
    assertEquals(stored.withReasonDraft(9, "ignored", itemCount, fieldCount), stored.sanitized(itemCount, fieldCount))
  }
}
