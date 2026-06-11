package it.evadid.workbook.model.interaction.basic

import munit.FunSuite

class LearningInteractionsSpec extends FunSuite {

  test("choice state sanitizes invalid and duplicate selections") {
    val state = ChoiceSelectionState(List(2, 7, 2, -1, 1))

    assertEquals(state.sanitized(optionCount = 3, allowMultiple = true), ChoiceSelectionState(List(2, 1)))
    assertEquals(state.sanitized(optionCount = 3, allowMultiple = false), ChoiceSelectionState(List(2)))
  }

  test("matching state pads missing left items and rejects invalid right indices") {
    val state = MatchingInteractionState(List(Some(2), Some(8)))

    assertEquals(
      state.sanitized(leftCount = 3, rightCount = 3),
      MatchingInteractionState(List(Some(2), None, None))
    )
  }

  test("fill-in-blank state keeps one value per blank") {
    val state = FillInBlanksState(List("Ada", "Lovelace", "extra"))

    assertEquals(state.sanitized(blankCount = 2), FillInBlanksState(List("Ada", "Lovelace")))
    assertEquals(state.withBlankValue(blankIndex = 1, value = "Byron", blankCount = 2), FillInBlanksState(List("Ada", "Byron")))
  }

  test("interaction state serializers round-trip JSON") {
    val choice = ChoiceSelectionState(List(0, 2))
    val dropdown = DropdownBlanksState(List(Some(1), None))
    val table = TableFillInState(List("3", "5"))

    assertEquals(ChoiceSelectionState.serializer.deserialize(ChoiceSelectionState.serializer.serialize(choice)), choice)
    assertEquals(DropdownBlanksState.serializer.deserialize(DropdownBlanksState.serializer.serialize(dropdown)), dropdown)
    assertEquals(TableFillInState.serializer.deserialize(TableFillInState.serializer.serialize(table)), table)
  }
}
