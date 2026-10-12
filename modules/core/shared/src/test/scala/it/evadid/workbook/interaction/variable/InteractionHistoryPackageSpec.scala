package it.evadid.workbook.interaction.variable

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.interaction.sync.UpdateImportance.*
import InteractionVariableState.*
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

class InteractionHistoryPackageSpec extends FunSuite {
  private val time = LocalDateTime.of(2025, 1, 1, 12, 0)
  private def state(value: Int, importance: it.evadid.workbook.interaction.sync.UpdateImportance, seconds: Long) =
    InteractionVariableState(value, importance, time.plusSeconds(seconds))
  test("empty histories and all-default histories clean predictably") {
    assertEquals(InteractionVariableHistory.empty[Int, Int, Int].lastStateOption, None)
    assertEquals(InteractionVariableHistory.empty[Int, Int, Int].cleanedHistory().events, Set.empty[InteractionVariableState[Int]])
    val first = state(1, DEFAULT, 0)
    val last = state(2, DEFAULT, 1)
    assertEquals(InteractionVariableHistory[Int, Int, Int](Set(first, last), Map.empty, Map.empty).cleanedHistory().events, Set(last))
  }
  test("history merges deduplicate events and drop defaults after a meaningful update") {
    val initial = state(1, DEFAULT, 0)
    val update = state(2, MINOR, 1)
    val history = InteractionVariableHistory[Int, Int, Int](Set(initial), Map.empty, Map.empty).withAddedEvent(update)
    assertEquals(history.events, Set(update))
    assertEquals(history.withAddedEvents(Set(update)).events, Set(update))
    assertEquals(history.withAddedEvents(Set(update)).events, Set(update))
    assertEquals(history.updateEvents(_.filter(_.value > 2)).events, Set.empty[InteractionVariableState[Int]])
  }
  test("typed histories reject malformed state values rather than silently changing types") {
    val invalid = ujson.Obj("events" -> ujson.Arr(ujson.Obj("value" -> "not a number",
      "updateImportance" -> "MINOR", "timestamp" -> time.toString)),
      "gradings" -> ujson.Arr(), "scaffoldings" -> ujson.Arr())
    intercept[Exception](read[InteractionVariableHistory[Int, Int, Int]](invalid))
  }
  test("typed histories and nested change models have default codecs") {
    val original = state(2, MINOR, 0)
    val history = InteractionVariableHistory[Int, Int, Int](Set(original), Map.empty, Map.empty)
    assertEquals(read[InteractionVariableState[Int]](write(original)), original)
    assertEquals(read[InteractionVariableHistory[Int, Int, Int]](write(history)), history)
    val designated = DesignatedInteractionState(3, time.plusSeconds(1))
    val changed = InteractionVariableStateChanged(original, designated)
    assertEquals(read[DesignatedInteractionState[Int]](write(designated)), designated)
    assertEquals(read[InteractionVariableStateChanged[Int]](write(changed)), changed)
  }
}
