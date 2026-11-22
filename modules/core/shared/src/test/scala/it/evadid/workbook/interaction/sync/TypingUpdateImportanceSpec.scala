package it.evadid.workbook.interaction.sync

import it.evadid.workbook.interaction.sync.TypingUpdateImportance.{TypedKey, decide}
import it.evadid.workbook.interaction.sync.UpdateImportance.{MAJOR, TEMPORARY}
import munit.FunSuite

import java.time.LocalDateTime

class TypingUpdateImportanceSpec extends FunSuite {
  private val now = LocalDateTime.of(2026, 11, 22, 12, 0)
  private def key(value: String, millisAgo: Long = 20) = Some(TypedKey(value, now.minusNanos(millisAgo * 1000000)))
  private val recentMajor = Some(now.minusSeconds(2))

  test("letters and deletions are temporary while the last major answer is recent") {
    List("a", "Z", "1", "Backspace", "Delete").foreach(typed =>
      assertEquals(decide(key(typed), recentMajor, now), TEMPORARY, typed))
  }

  test("whitespace and structural characters commit the answer") {
    (List(" ", "Enter", "Tab") ++ "()[]{}.,-+*;".map(_.toString)).foreach(typed =>
      assertEquals(decide(key(typed), recentMajor, now), MAJOR, typed))
  }

  test("a keystroke commits once the last major answer is older than five seconds, or there is none") {
    assertEquals(decide(key("a"), Some(now.minusSeconds(6)), now), MAJOR)
    assertEquals(decide(key("a"), Some(now.minusSeconds(5)), now), TEMPORARY, "the interval boundary is still recent")
    assertEquals(decide(key("a"), None, now), MAJOR)
  }

  test("changes without a recent key, such as block edits, are major") {
    assertEquals(decide(None, recentMajor, now), MAJOR)
    assertEquals(decide(key("a", millisAgo = 1500), recentMajor, now), MAJOR)
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
    assertEquals(importance, Some(MAJOR), "the first keystroke commits: there is no recent major answer")
    press("o", "fo")
    assertEquals(importance, Some(TEMPORARY))
    press(";", "fo;")
    assertEquals(importance, Some(MAJOR))
    typed.set(None)
    bound.set("fo; // dragged block")
    assertEquals(importance, Some(MAJOR), "a change without a key is a deliberate edit")
    assertEquals(variable.currentHistory.allStates.map(_.newState), Set("f", "fo;", "fo; // dragged block"),
      "the superseded temporary answer is cleaned")
  }
}
