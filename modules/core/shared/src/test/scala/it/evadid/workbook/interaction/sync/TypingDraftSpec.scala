package it.evadid.workbook.interaction.sync

import it.evadid.workbook.interaction.sync.TypingUpdateImportance.TypedKey
import munit.FunSuite

import java.time.LocalDateTime

class TypingDraftSpec extends FunSuite {
  private val start = LocalDateTime.of(2026, 11, 23, 12, 0)
  private def at(millis: Long) = start.plusNanos(millis * 1000000)
  private def typed(draft: TypingDraft[String], key: String, text: String, millis: Long) =
    draft.edit(text, Some(TypedKey(key, at(millis))), at(millis))
  private val opened = TypingDraft("fo", start, Seq.empty)

  test("keystrokes inside a token stay local until a token-ending key commits them") {
    val typing = typed(typed(opened, "o", "foo", 100), "r", "foor", 200)
    assertEquals(typing.committed, "fo")
    assertEquals(typing.value, "foor")
    assertEquals(typing.pending.map(_.importance), Seq(UpdateImportance.TEMPORARY, UpdateImportance.TEMPORARY))
    val ended = typed(typing, ";", "foor;", 300)
    assertEquals((ended.committed, ended.pending), ("foor;", Seq.empty))
  }

  test("a quiet period commits only the latest TEMPORARY edit") {
    val first = typed(opened, "o", "foo", 100)
    val second = typed(first, "r", "foor", 200)
    assertEquals(second.settle(first.latestEdit.get, at(1100)), second, "a newer edit supersedes the earlier check")
    assertEquals(second.settle(second.latestEdit.get, at(1200)).committed, "foor")
  }

  test("a keystroke five seconds after the last commit, and a change without a key, commit at once") {
    assertEquals(typed(opened, "o", "foo", 6000).committed, "foo")
    assertEquals(opened.edit("dragged", None, at(100)).committed, "dragged")
  }

  test("an outside answer replaces the draft; the echo of its own commit does not") {
    val typing = typed(opened, "o", "foo", 100)
    assertEquals(typing.received("fo", at(150)), typing)
    assertEquals(typing.received("restored", at(150)), TypingDraft("restored", at(150), Seq.empty))
    assertEquals(typing.flush(at(200)).committed, "foo")
  }
}
