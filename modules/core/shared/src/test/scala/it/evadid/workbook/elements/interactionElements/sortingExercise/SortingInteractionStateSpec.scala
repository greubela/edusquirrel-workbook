package it.evadid.workbook.elements.interactionElements.sortingExercise

import munit.FunSuite

class SortingInteractionStateSpec extends FunSuite {

  private val itemCount = 3
  private val fieldCount = 2

  test("activeItemIndex returns first unassigned item") {
    val state = SortingInteractionState(
      List(
        Some(0),
        None,
        None
      )
    )

    assertEquals(state.activeItemIndex(itemCount, fieldCount), Some(1))
  }

  test("tryAssign accepts correct placement for active item only") {
    val state = SortingInteractionState.initial(itemCount)

    val (afterCorrect, result) = state.tryAssign(0, 1, itemCount, fieldCount, correctFieldIndex = 1)
    assertEquals(result, AssignmentResult.Correct)
    assertEquals(afterCorrect.placedFieldIndexByItem(0), Some(1))
    assertEquals(afterCorrect.activeItemIndex(itemCount, fieldCount), Some(1))

    val (afterWrong, wrongResult) = afterCorrect.tryAssign(1, 0, itemCount, fieldCount, correctFieldIndex = 1)
    assertEquals(wrongResult, AssignmentResult.Wrong)
    assertEquals(afterWrong.placedFieldIndexByItem(1), None)
  }

  test("session error count persists after correct placement") {
    val state = SortingInteractionState.initial(itemCount).recordWrongAttempt()
    val (afterCorrect, result) = state.tryAssign(0, 0, itemCount, fieldCount, correctFieldIndex = 0)
    assertEquals(result, AssignmentResult.Correct)

    val merged = state.copy(placedFieldIndexByItem = afterCorrect.placedFieldIndexByItem)
    assertEquals(merged.sessionErrorCount, 1)
  }

  test("finalizeAttempt copies session errors to last session and clears session counter") {
    val state = SortingInteractionState.initial(itemCount).recordWrongAttempt().recordWrongAttempt()
    val finalized = state.finalizeAttempt()

    assertEquals(finalized.lastSessionErrorCount, 2)
    assertEquals(finalized.sessionErrorCount, 0)
  }

  test("resetAttempt clears placements and session errors") {
    val state = SortingInteractionState(
      List(Some(0), None, Some(1)),
      sessionErrorCount = 3,
      lastSessionErrorCount = 1
    )
    val reset = state.resetAttempt(itemCount)

    assertEquals(reset.placedFieldIndexByItem, List(None, None, None))
    assertEquals(reset.sessionErrorCount, 0)
    assertEquals(reset.lastSessionErrorCount, 1)
  }

  test("recordWrongPlacement increments errors and stores preview") {
    val state = SortingInteractionState.initial(itemCount).recordWrongPlacement(0, 1)

    assertEquals(state.sessionErrorCount, 1)
    assertEquals(state.wrongPlacementPreview, Some((0, 1)))
    assertEquals(state.placedFieldIndexByItem(0), None)
  }

  test("clearWrongPlacementPreview removes preview only") {
    val state = SortingInteractionState.initial(itemCount)
      .recordWrongPlacement(0, 1)
      .clearWrongPlacementPreview()

    assertEquals(state.sessionErrorCount, 1)
    assertEquals(state.wrongPlacementPreview, None)
  }

  test("serializer round-trips state including error counts") {
    val state = SortingInteractionState(
      List(
        Some(0),
        None,
        Some(1)
      ),
      sessionErrorCount = 2,
      lastSessionErrorCount = 4
    )

    val roundTrip = SortingInteractionState.serializer.deserialize(
      SortingInteractionState.serializer.serialize(state)
    )
    assertEquals(roundTrip, state)
  }
}
