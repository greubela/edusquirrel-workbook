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
    assertEquals(InteractionVariableHistory.empty[Int].lastStateOption, None)
    assertEquals(InteractionVariableHistory.empty[Int].cleanedHistory().events, Set.empty[InteractionVariableState[Int]])
    val first = state(1, DEFAULT, 0)
    val last = state(2, DEFAULT, 1)
    assertEquals(InteractionVariableHistory(Set(first, last)).cleanedHistory().events, Set(last))
  }
  test("history merges deduplicate events and drop defaults after a meaningful update") {
    val initial = state(1, DEFAULT, 0)
    val update = state(2, MINOR, 1)
    val history = InteractionVariableHistory(Set(initial)).withAddedEvent(update)
    assertEquals(history.events, Set(update))
    assertEquals(history.withAddedEvents(Set(update)).events, Set(update))
    assertEquals(history.withAddedEvents(InteractionVariableHistory(Set(update))).events, Set(update))
    assertEquals(history.map(_.filter(_.value > 2)).events, Set.empty[InteractionVariableState[Int]])
  }
  test("serialized histories partition bad values and retain valid states") {
    val original = state(2, MINOR, 0)
    val serialized = original.serialized(Serializer.intDecimalIO.map(_.toInt, BigInt(_)))
    val invalid = serialized.copy(serializedValue = "not a number", timestamp = time.plusSeconds(1))
    val history = InteractionVariableHistorySerialized(Set(serialized, invalid))
    val io = Serializer.intDecimalIO.map(_.toInt, BigInt(_))
    val (valid, failed) = history.tryDeserialize(io)
    assertEquals(valid.events, Set(original))
    assertEquals(failed.states, Set(invalid))
    assertEquals(history.deserializeIgnoreErrors(io), valid)
    assertEquals(InteractionVariableHistory.empty[Int].withAddedEvents(history, io), valid)
    assertEquals(history.lastStateOption, Some(invalid))
    assertEquals(InteractionVariableState(io, serialized), original)
  }
  test("typed histories and nested change models have default codecs") {
    val original = state(2, MINOR, 0)
    val history = InteractionVariableHistory(Set(original))
    assertEquals(read[InteractionVariableState[Int]](write(original)), original)
    assertEquals(read[InteractionVariableHistory[Int]](write(history)), history)
    val designated = DesignatedInteractionState(3, time.plusSeconds(1))
    val changed = InteractionVariableStateChanged(original, designated)
    assertEquals(read[DesignatedInteractionState[Int]](write(designated)), designated)
    assertEquals(read[InteractionVariableStateChanged[Int]](write(changed)), changed)
  }
}
