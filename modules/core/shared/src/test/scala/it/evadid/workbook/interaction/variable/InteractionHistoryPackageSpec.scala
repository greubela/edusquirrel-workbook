package it.evadid.workbook.interaction.variable

import it.evadid.workbook.interaction.sync.{EventSyncStrategy, UpdateImportance}
import it.evadid.workbook.interaction.sync.UpdateImportance.*
import it.evadid.workbook.interaction.variable.events.*
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

import it.evadid.workbook.abstractions.InteractionFormat.*

class InteractionHistoryPackageSpec extends FunSuite {
  private val time = LocalDateTime.of(2025, 1, 1, 12, 0)
  
  private def answer(value: Int, importance: UpdateImportance): AnswerChangedEvent[TextInteractionFormat.type] =
    AnswerChangedEvent[TextInteractionFormat.type](value.toString, time.plusSeconds(10), importance)
  
  private def majorAnswer(value: Int): AnswerChangedEvent[TextInteractionFormat.type] =
    AnswerChangedEvent[TextInteractionFormat.type](value.toString, time.plusSeconds(10), UpdateImportance.MAJOR)
  
  test("event identities survive roundtrips, retries and copies without changing timestamps") {
    val first = majorAnswer(1)
    val independent = majorAnswer(1)
    assertNotEquals(first.eventId, independent.eventId)
    assertEquals(first.timestamp, independent.timestamp)
    val original = InteractionVariableHistory.empty[TextInteractionFormat.type].addEvent(first)
    val restored = read[InteractionVariableHistory[TextInteractionFormat.type]](write(original))
    assertEquals(restored, original)
    assertEquals(original.mergeWith(restored).size, 1)
    assertEquals(original.addEvent(independent).size, 2)
    val revised = first.copy[TextInteractionFormat.type](newState = "revised")
    assertEquals(revised.eventId, first.eventId)
    assertEquals(original.addEvent(revised).events.values, Set[InteractionEvent[TextInteractionFormat.type]](revised))
    assertEquals(original.mergeWith(original.addEvent(revised)).events.values, Set[InteractionEvent[TextInteractionFormat.type]](revised))
    val codec = TextInteractionFormat.codecs.eventSerializer
    val malformed = ujson.read(codec.serialize(first))
    malformed.obj.remove("eventId")
    intercept[Exception](codec.deserialize(ujson.write(malformed)))
  }

  test("publishing a state or result again preserves its event identity") {
    val state = InteractionVariableState("answer", UpdateImportance.MAJOR, time)
    val first = InteractionVariableHistory.empty[TextInteractionFormat.type].addState(state)
    assertEquals(first.addState(state), first)
  }

  test("empty histories have no states and stay empty when cleaned") {
    val empty = InteractionVariableHistory.empty[TextInteractionFormat.type]
    assertEquals(empty.allStates.size, 0)
    assertEquals(empty.cleanedHistory().events.values.size, 0)
  }
  
  test("cleanWithStrategy filters events based on importance") {
    val temp = AnswerChangedEvent[TextInteractionFormat.type]("1", time.plusSeconds(5), UpdateImportance.TEMPORARY)
    val major = AnswerChangedEvent[TextInteractionFormat.type]("2", time.plusSeconds(10), UpdateImportance.MAJOR)
    val minor = AnswerChangedEvent[TextInteractionFormat.type]("3", time.plusSeconds(15), UpdateImportance.MINOR)
    
    val history = InteractionVariableHistory.empty[TextInteractionFormat.type]
      .addEvent(temp)
      .addEvent(major)
      .addEvent(minor)
    
    // Keep all - should have all 3
    val keepAll = history.cleanWithStrategy(EventSyncStrategy.SYNC_EVERYTHING)
    assertEquals(keepAll.events.values.size, 3)
    
    // Keep the latest major event; newer minor events are outside this strategy.
    val keepLastMajor = history.cleanWithStrategy(EventSyncStrategy.KEEP_LAST_MAJOR)
    assertEquals(keepLastMajor.events.values, Set[InteractionEvent[TextInteractionFormat.type]](major))
  }
}
