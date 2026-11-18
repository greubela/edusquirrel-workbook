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
    assertEquals(original.addEvent(revised).events.values, Set(revised))
    assertEquals(original.mergeWith(original.addEvent(revised)).events.values, Set(revised))
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

  test("empty histories have no states and clean to empty") {
    val empty = InteractionVariableHistory.empty[TextInteractionFormat.type]
    assertEquals(empty.allStates.size, 0)
    assertEquals(empty.clean().events.values.size, 0)
  }
  
  test("adding events creates history and clean removes TEMPORARY events") {
    val tempAnswer = AnswerChangedEvent[TextInteractionFormat.type]("1", time.plusSeconds(5), UpdateImportance.TEMPORARY)
    val majorAnswer = AnswerChangedEvent[TextInteractionFormat.type]("2", time.plusSeconds(10), UpdateImportance.MAJOR)
    val history = InteractionVariableHistory.empty[TextInteractionFormat.type]
      .addEvent(tempAnswer)
      .addEvent(majorAnswer)
    
    assertEquals(history.events.values.size, 2)
    assertEquals(history.allStates.size, 2)
    assertEquals(history.clean().events.values.size, 1)
    assertEquals(history.clean().events.values.head.asInstanceOf[AnswerChangedEvent[TextInteractionFormat.type]].newState, "2")
  }
  
  test("canonical history codecs preserve roundtrip") {
    val original = majorAnswer(42)
    val history = InteractionVariableHistory.empty[TextInteractionFormat.type].addEvent(original)
    
    val written = write(history)
    val readBack = read[InteractionVariableHistory[TextInteractionFormat.type]](written)
    assertEquals(readBack.events.values.size, 1)
    
    val binaryWritten = writeBinary(history)
    val binaryReadBack = readBinary[InteractionVariableHistory[TextInteractionFormat.type]](binaryWritten)
    assertEquals(binaryReadBack.events.values.size, 1)
  }
  
  test("merging histories combines events and handles duplicates") {
    val event1 = majorAnswer(1)
    val event2 = majorAnswer(2)
    val event3 = majorAnswer(3)
    
    val history1 = InteractionVariableHistory.empty[TextInteractionFormat.type].addEvent(event1).addEvent(event2)
    val history2 = InteractionVariableHistory.empty[TextInteractionFormat.type].addEvent(event2).addEvent(event3)
    
    val merged = history1.mergeWith(history2)
    assertEquals(merged.events.values.size, 3)
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
  
  test("mergeWith applies strategy correctly when merging") {
    val temp = AnswerChangedEvent[TextInteractionFormat.type]("1", time.plusSeconds(5), UpdateImportance.TEMPORARY)
    val major = AnswerChangedEvent[TextInteractionFormat.type]("2", time.plusSeconds(10), UpdateImportance.MAJOR)
    
    val history1 = InteractionVariableHistory.empty[TextInteractionFormat.type].addEvent(temp).addEvent(major)
    val history2 = InteractionVariableHistory.empty[TextInteractionFormat.type].addEvent(major)
    
    val merged = history1.mergeWith(history2)
    assertEquals(merged.events.values.size, 2)
    assertEquals(merged.events.values.count(_.updateImportance == UpdateImportance.MAJOR), 1)
  }
}
