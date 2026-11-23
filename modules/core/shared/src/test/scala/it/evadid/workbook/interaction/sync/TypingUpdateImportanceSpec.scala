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
}
