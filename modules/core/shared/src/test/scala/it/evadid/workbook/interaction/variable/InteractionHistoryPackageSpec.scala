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
    assertEquals(InteractionVariableHistory.empty[Int, Int, Int].historyStates.lastStateOption, None)
    assertEquals(InteractionVariableHistory.empty[Int, Int, Int].historyStates.cleanedHistory().events, Set.empty[InteractionVariableState[Int]])
    val first = state(1, DEFAULT, 0)
    val last = state(2, DEFAULT, 1)
    assertEquals(InteractionVariableHistory[Int, Int, Int](InteractionVariableStateHistory[Int](Set(first, last)), new InteractionVariableGradingHistory[Int, Int](gradingsMap = Map.empty), new InteractionVariableScaffoldingHistory[Int, Int](scaffoldingMap = Map.empty)).historyStates.cleanedHistory().events, Set(last))
  }
  test("canonical history codecs preserve explicitly constructed default snapshots") {
    val history = InteractionVariableHistory[Int, Int, Int](Set(state(1, DEFAULT, 0), state(2, DEFAULT, 1)))
    assertEquals(read[InteractionVariableHistory[Int, Int, Int]](write(history)), history)
    assertEquals(readBinary[InteractionVariableHistory[Int, Int, Int]](writeBinary(history)), history)
  }
  test("history merges deduplicate events and drop defaults after a meaningful update") {
    val initial = state(1, DEFAULT, 0)
    val update = state(2, MINOR, 1)
    val history = InteractionVariableHistory[Int, Int, Int](InteractionVariableStateHistory[Int](Set(initial)), new InteractionVariableGradingHistory[Int, Int](gradingsMap = Map.empty), new InteractionVariableScaffoldingHistory[Int, Int](scaffoldingMap = Map.empty)).addState(update)
    assertEquals(history.historyStates.events, Set(update))
    assertEquals(history.addStates(Set(update)).historyStates.events, Set(update))
    assertEquals(history.addStates(Set(update)).historyStates.events, Set(update))
    assertEquals(history.updateStates(_.filter(_.value > 2)).historyStates.events, Set.empty[InteractionVariableState[Int]])
  }
  test("typed histories reject malformed state values rather than silently changing types") {
    val invalid = ujson.Obj("events" -> ujson.Arr(ujson.Obj("value" -> "not a number",
      "updateImportance" -> "MINOR", "timestamp" -> time.toString)),
      "gradings" -> ujson.Arr(), "scaffoldings" -> ujson.Arr())
    intercept[Exception](read[InteractionVariableHistory[Int, Int, Int]](invalid))
  }
  test("typed histories and nested change models have default codecs") {
    val original = state(2, MINOR, 0)
    val history = InteractionVariableHistory[Int, Int, Int](InteractionVariableStateHistory[Int](Set(original)), new InteractionVariableGradingHistory[Int, Int](gradingsMap = Map.empty), new InteractionVariableScaffoldingHistory[Int, Int](scaffoldingMap = Map.empty))
    assertEquals(read[InteractionVariableState[Int]](write(original)), original)
    assertEquals(read[InteractionVariableHistory[Int, Int, Int]](write(history)), history)
    val designated = DesignatedInteractionState(3, time.plusSeconds(1))
    val changed = InteractionVariableStateChanged(original, designated)
    assertEquals(read[DesignatedInteractionState[Int]](write(designated)), designated)
    assertEquals(read[InteractionVariableStateChanged[Int]](write(changed)), changed)
  }
}
