package it.evadid.workbook.interaction.sync.model

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}
import munit.FunSuite
import upickle.default.*

import java.time.LocalDateTime

class HistoryJsonSpec extends FunSuite {
  private val time = LocalDateTime.of(2026, 1, 1, 0, 0)
  private def history[T](state: T, seconds: Int) = {
    val feedback = ScaffoldingResult(state, LanguageMap.universalMap[AppLanguage.HumanLanguage]("Try again"),
      FeedbackEntity.TestEntity(), Some(List(1, 2, 3)))
    InteractionVariableHistory[T, Int, List[Int]](
      Set(InteractionVariableState(state, UpdateImportance.MAJOR, time.plusSeconds(seconds))),
      Map.empty, Map(state -> feedback))
  }

  test("merging histories preserves feedback with string map keys and chooses the first duplicate") {
    val first = history("Grüße", 1)
    val second = history("second", 2)
    val merged = read[InteractionVariableHistory[String, Int, List[Int]]](HistoryJson.merge(List(write(first), write(second), write(first))))
    assertEquals(merged.events, first.events ++ second.events)
    assertEquals(merged.scaffoldings, first.scaffoldings ++ second.scaffoldings)
    assertEquals(HistoryJson.lastTimestamp(write(merged)), Some(time.plusSeconds(2)))
  }

  test("merging histories preserves feedback with structured map keys") {
    val first = history(List("a", "b"), 1)
    val second = history(List("c"), 2)
    val merged = read[InteractionVariableHistory[List[String], Int, List[Int]]](HistoryJson.merge(List(write(first), write(second))))
    assertEquals(merged.events, first.events ++ second.events)
    assertEquals(merged.scaffoldings, first.scaffoldings ++ second.scaffoldings)
  }

  test("an interaction restores legacy database envelopes and mixed old and new histories") {
    val variable = TextInteraction("answer").interactionVariable
    val old = ujson.Obj("states" -> ujson.Arr(ujson.Obj("serializedValue" -> "older",
      "updateImportance" -> "MAJOR", "timestamp" -> time.toString)))
    val envelope = ujson.write(ujson.Obj("keyForSerialisation" -> "answer_history", "fullHistory" -> ujson.write(old)))
    val newer = InteractionVariableHistory[String, Int, Int](
      Set(InteractionVariableState("newer", UpdateImportance.MAJOR, time.plusSeconds(1))), Map.empty, Map.empty)
    val restored = variable.deserializeHistory(HistoryJson.merge(List(envelope, write(newer))))
    assertEquals(restored.events.map(_.value), Set("older", "newer"))
    assertEquals(restored.lastStateOption.map(_.value), Some("newer"))
  }
  test("legacy text retains literal quotes instead of being interpreted as JSON") {
    val variable = TextInteraction("answer").interactionVariable
    val old = ujson.Obj("states" -> ujson.Arr(ujson.Obj("serializedValue" -> "\"quoted\"",
      "updateImportance" -> "MAJOR", "timestamp" -> time.toString)))
    assertEquals(variable.deserializeHistory(ujson.write(old)).lastStateOption.map(_.value), Some("\"quoted\""))
  }

}
