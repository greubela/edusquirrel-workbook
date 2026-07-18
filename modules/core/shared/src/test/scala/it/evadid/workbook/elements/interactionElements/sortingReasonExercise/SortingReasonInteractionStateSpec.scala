package it.evadid.workbook.elements.interactionElements.sortingReasonExercise

import it.evadid.workbook.elements.interactionElements.sortingExercise.AssignmentResult
import munit.FunSuite

class SortingReasonInteractionStateSpec extends FunSuite {

  private val itemCount = 2
  private val fieldCount = 2

  test("reason must be confirmed before assignment is accepted") {
    val state = SortingReasonInteractionState.initial(itemCount)
      .withReasonDraft(0, "weil RLE verlustfrei ist", itemCount, fieldCount)
      .confirmReason(0, itemCount, fieldCount)

    val (afterCorrect, result) = state.tryAssign(0, 0, itemCount, fieldCount, correctFieldIndex = 0)
    assertEquals(result, AssignmentResult.Correct)
    assertEquals(afterCorrect.placedFieldIndexByItem(0), Some(0))
  }

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

  test("session error count persists after correct placement") {
    val state = SortingReasonInteractionState.initial(itemCount)
      .withReasonDraft(0, "begründung", itemCount, fieldCount)
      .confirmReason(0, itemCount, fieldCount)
      .recordWrongAttempt()

    val (afterCorrect, _) = state.tryAssign(0, 0, itemCount, fieldCount, correctFieldIndex = 0)
    val merged = state.copy(placedFieldIndexByItem = afterCorrect.placedFieldIndexByItem)
    assertEquals(merged.sessionErrorCount, 1)
  }

  test("serializer round-trips reason state") {
    val state = SortingReasonInteractionState(
      List(Some(1), None),
      List("weil jpeg verlustbehaftet ist", ""),
      List(true, false),
      sessionErrorCount = 1,
      lastSessionErrorCount = 3
    )

    val roundTrip = SortingReasonInteractionState.serializer.deserialize(
      SortingReasonInteractionState.serializer.serialize(state)
    )
    assertEquals(roundTrip, state)
  }
}
