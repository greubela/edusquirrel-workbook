package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataSuccess
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.interaction.variable.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite

import java.time.LocalDateTime

/** Bound editor states, feedback sessions and the textarea importance policy. */
class InteractionVariableBindingSpec extends FunSuite {
  private val context = TestExecutionContext()
  private val start = LocalDateTime.of(2026, 1, 1, 12, 0)

  test("bound states can derive the update importance from the change") {
    val variable = TextInteraction("relevance").interactionVariable
    val seen = State(Vector.empty[(String, String)])
    val bound = variable.createBoundStateWithUpdateLogic(change => {
      seen.update(_ :+ (change.lastState.value -> change.newState.value))
      UpdateImportance.MAJOR
    }, context)
    bound.set("a")
    assertEquals(seen.now(), Vector("" -> "a"))
    assertEquals(variable.safeLastState.updateImportance, UpdateImportance.MAJOR)
    assertEquals(variable.asAsync.stateNow(), AsyncDataSuccess("a"))
  }

  test("a new feedback session restores the default history, rebinding the same owner keeps it") {
    val variable = TextInteraction("session").interactionVariable
    variable.bindFeedbackSession("first")
    variable.setStateFromUserInteraction("mine", UpdateImportance.MAJOR, LocalDateTime.now().plusSeconds(1), context)
    variable.bindFeedbackSession("first")
    assertEquals(variable.currentValue, "mine")
    variable.bindFeedbackSession("second")
    assertEquals(variable.currentValue, "")
  }

  test("textarea edits are classified by timing, length and the typed character") {
    def change(before: String, after: String, seconds: Long = 1) = TextInteraction.decideTextareaUpdateImportance(
      InteractionVariableStateChanged(InteractionVariableState(before, UpdateImportance.MINOR, start),
        DesignatedInteractionState(after, start.plusSeconds(seconds))))
    assertEquals(change("same", "same"), UpdateImportance.TEMPORARY)
    assertEquals(change("a", "ab", seconds = 11), UpdateImportance.MAJOR)
    assertEquals(change("a", "a."), UpdateImportance.MAJOR)
    assertEquals(change("a", "a,"), UpdateImportance.MINOR)
    assertEquals(change("a", "ab"), UpdateImportance.TEMPORARY)
    assertEquals(change("abc", "a"), UpdateImportance.MINOR)
    assertEquals(TextInteraction.factory.callConstructor("t"), TextInteraction("t"))
  }
}
