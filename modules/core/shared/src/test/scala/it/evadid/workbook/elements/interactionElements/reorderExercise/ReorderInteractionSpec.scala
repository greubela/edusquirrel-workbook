package it.evadid.workbook.elements.interactionElements.reorderExercise

import it.evadid.core.datastructures.language.AppLanguage.Python
import munit.FunSuite
import upickle.default.*

/** Drag-and-drop ordering and its correctness rules, shared by the reorder and sketch-download renderers. */
class ReorderInteractionSpec extends FunSuite {
  private val lines = List("a = 1", "b = a + 1", "print(b)")
  private def state(order: Int*) = ReorderInteractionState.forCode(lines, order, lines.indices, ReorderType.CODELINES(Python))
  private val exercise = ReorderInteraction.ReorderCodeInteraction("order", lines, Python)

  test("dropping an element inserts it before the target position, as the drag gesture shows") {
    val start = state(0, 1, 2)
    assertEquals(start.moveElementToIndex(0, 3).currentOrder, List(1, 2, 0), "to the end")
    assertEquals(start.moveElementToIndex(2, 0).currentOrder, List(2, 0, 1), "to the front")
    assertEquals(start.moveElementToIndex(0, 2).currentOrder, List(1, 0, 2), "down by one")
    assertEquals(start.moveElementToIndex(1, 1).currentOrder, List(0, 1, 2), "onto itself")
    assertEquals(start.moveElementToIndex(1, 2).currentOrder, List(0, 1, 2), "just after itself")
  }

  test("out-of-range drops are clamped and unknown elements leave the order unchanged") {
    val start = state(2, 0, 1)
    assertEquals(start.moveElementToIndex(1, -5).currentOrder, List(1, 2, 0))
    assertEquals(start.moveElementToIndex(2, 99).currentOrder, List(0, 1, 2))
    assert(start.moveElementToIndex(7, 0) eq start)
  }

  test("a seed shuffles the lines into the same permutation every time") {
    val first = ReorderInteractionState.initStateFromElementsAndSeed(lines, 42, ReorderType.CODELINES(Python))
    assertEquals(ReorderInteractionState.initStateFromElementsAndSeed(lines, 42, ReorderType.CODELINES(Python)), first)
    assertEquals(first.currentOrder.sorted, lines.indices.toList)
    assertEquals(first.correctOrder, lines.indices.toList)
    assertEquals(exercise.defaultValue, ReorderInteractionState.initStateFromElementsAndSeed(lines, 0, ReorderType.CODELINES(Python)))
  }

  test("without constraints only the authored order is correct") {
    assert(ReorderCorrectness.isOrderCorrect(exercise, state(0, 1, 2)))
    assert(!ReorderCorrectness.isOrderCorrect(exercise, state(1, 0, 2)))
  }

  test("with constraints every order that respects them is correct") {
    // Only the assignment of `a` must come before its use; the print may move freely relative to `a`.
    val constrained = exercise.copy(orderConstraints = List(0 -> 1, 1 -> 2))
    val loose = exercise.copy(orderConstraints = List(0 -> 1))
    assert(ReorderCorrectness.isOrderCorrect(constrained, state(0, 1, 2)))
    assert(!ReorderCorrectness.isOrderCorrect(constrained, state(0, 2, 1)))
    assertEquals(List(state(0, 1, 2), state(2, 0, 1), state(0, 2, 1), state(1, 0, 2)).map(ReorderCorrectness.isOrderCorrect(loose, _)),
      List(true, true, true, false))
  }

  test("orders that are not a permutation of the lines are rejected") {
    intercept[AssertionError](state(0, 0, 1))
    intercept[AssertionError](state(0, 1))
    assertEquals(state(2, 1, 0).sanitizeOrder(List(0, 0, 2)), lines.indices.toList, "a malformed order falls back to the listed order")
  }

  test("a moved state round-trips its serialized form") {
    val moved = state(2, 0, 1).moveElementToIndex(1, 0)
    assertEquals(read[ReorderInteractionState[String]](write(moved)), moved)
  }
}
