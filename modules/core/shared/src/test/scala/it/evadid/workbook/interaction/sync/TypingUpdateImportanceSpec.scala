package it.evadid.workbook.interaction.sync

import it.evadid.workbook.interaction.sync.TypingUpdateImportance.{TypedKey, decide}
import it.evadid.workbook.interaction.sync.UpdateImportance.{MAJOR, TEMPORARY}
import munit.FunSuite

import java.time.LocalDateTime

class TypingUpdateImportanceSpec extends FunSuite {
  private val now = LocalDateTime.of(2026, 11, 22, 12, 0)
  private def key(value: String, millisAgo: Long = 20) = Some(TypedKey(value, now.minusNanos(millisAgo * 1000000)))

  test("letters, deletions, spaces and structural characters are temporary") {
    (List("a", "Z", "1", "Backspace", "Delete", " ", "Tab") ++ "()[]{}.,-+*;".map(_.toString)).foreach(typed =>
      assertEquals(decide(key(typed), now), TEMPORARY, typed))
  }

  test("only Enter commits the answer while typing") {
    assertEquals(decide(key("Enter"), now), MAJOR)
  }

  test("changes without a recent key, such as block edits, are major") {
    assertEquals(decide(None, now), MAJOR)
    assertEquals(decide(key("a", millisAgo = 1500), now), MAJOR)
  }

  test("a typing binding records each change with the importance of the key that produced it") {
    import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
    import it.evadid.workbook.meta.ExecutionContextInformation
    val variable = TextInteraction("typing").interactionVariable
    val typed = it.evadid.core.datastructures.state.State(Option.empty[TypedKey])
    val bound = variable.createBoundStateForTyping(() => typed.now(), ExecutionContextInformation.default)
    def importance = variable.currentHistory.historyStates.lastStateOption.map(_.updateImportance)
    def press(key: String, text: String): Unit = { typed.set(Some(TypedKey(key, LocalDateTime.now()))); bound.set(text) }
    press("f", "f")
    assertEquals(importance, Some(TEMPORARY))
    press(";", "f;")
    assertEquals(importance, Some(TEMPORARY), "structural characters do not commit")
    // What the client does once typing has paused for idleCommitDelay.
    variable.commitCurrentState(ExecutionContextInformation.default)
    assertEquals(importance, Some(MAJOR), "a pause commits the typed answer")
    press("o", "f;o")
    assertEquals(importance, Some(TEMPORARY))
    press("Enter", "f;o\n")
    assertEquals(importance, Some(MAJOR))
    typed.set(None)
    bound.set("f;o\n// dragged block")
    assertEquals(importance, Some(MAJOR), "a change without a key is a deliberate edit")
    assertEquals(variable.currentHistory.allStates.map(_.newState), Set("f;", "f;o\n", "f;o\n// dragged block"),
      "the superseded temporary answers are cleaned")
  }
}
