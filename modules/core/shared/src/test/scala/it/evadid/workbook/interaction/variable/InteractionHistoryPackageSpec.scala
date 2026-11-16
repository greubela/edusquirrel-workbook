package it.evadid.workbook.interaction.variable

import it.evadid.workbook.interaction.sync.UpdateImportance.*
import it.evadid.workbook.interaction.variable.events.*
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

class InteractionHistoryPackageSpec extends FunSuite {
  private val time = LocalDateTime.of(2025, 1, 1, 12, 0)
  
  private def answer(value: Int, importance: UpdateImportance): AnswerChangedEvent[Int, Int, Int] =
    AnswerChangedEvent(value, time.plusSeconds(10), importance)
  
  private def majorAnswer(value: Int): AnswerChangedEvent[Int, Int, Int] =
    AnswerChangedEvent(value, time.plusSeconds(10), UpdateImportance.MAJOR)
  
  test("empty histories have no states and clean to empty") {
    val empty = InteractionVariableHistory.empty[Int, Int, Int]
    assertEquals(empty.allStates.size, 0)
    assertEquals(empty.clean().events.values.size, 0)
  }
  
  test("adding events creates history and clean removes TEMPORARY events") {
    val tempAnswer = AnswerChangedEvent(1, time.plusSeconds(5), UpdateImportance.TEMPORARY)
    val majorAnswer = AnswerChangedEvent(2, time.plusSeconds(10), UpdateImportance.MAJOR)
    val history = InteractionVariableHistory.empty[Int, Int, Int]
      .addEvent(tempAnswer)
      .addEvent(majorAnswer)
    
    assertEquals(history.events.values.size, 2)
    assertEquals(history.allStates.size, 2)
    assertEquals(history.clean().events.values.size, 1)
    assertEquals(history.clean().events.values.head.asInstanceOf[AnswerChangedEvent[Int, Int, Int]].newState, 2)
  }
  
  test("canonical history codecs preserve roundtrip") {
    val original = majorAnswer(42)
    val history = InteractionVariableHistory.empty[Int, Int, Int].addEvent(original)
    
    val written = write(history)
    val readBack = read[InteractionVariableHistory[Int, Int, Int]](written)
    assertEquals(readBack.events.values.size, 1)
    
    val binaryWritten = writeBinary(history)
    val binaryReadBack = readBinary[InteractionVariableHistory[Int, Int, Int]](binaryWritten)
    assertEquals(binaryReadBack.events.values.size, 1)
  }
  
  test("merging histories combines events and handles duplicates") {
    val event1 = majorAnswer(1)
    val event2 = majorAnswer(2)
    val event3 = majorAnswer(3)
    
    val history1 = InteractionVariableHistory.empty[Int, Int, Int].addEvent(event1).addEvent(event2)
    val history2 = InteractionVariableHistory.empty[Int, Int, Int].addEvent(event2).addEvent(event3)
    
    val merged = history1.mergeWith(history2)
    assertEquals(merged.events.values.size, 3)
  }
  
  test("cleanWithStrategy filters events based on importance") {
    val temp = AnswerChangedEvent(1, time.plusSeconds(5), UpdateImportance.TEMPORARY)
    val major = AnswerChangedEvent(2, time.plusSeconds(10), UpdateImportance.MAJOR)
    val minor = AnswerChangedEvent(3, time.plusSeconds(15), UpdateImportance.MINOR)
    
    val history = InteractionVariableHistory.empty[Int, Int, Int]
      .addEvent(temp)
      .addEvent(major)
      .addEvent(minor)
    
    // Keep all - should have all 3
    val keepAll = history.cleanWithStrategy(EventSyncStrategy.KEEP_ALL)
    assertEquals(keepAll.events.values.size, 3)
    
    // Keep last major - should have 2 (major and minor)
    val keepLastMajor = history.cleanWithStrategy(EventSyncStrategy.KEEP_LAST_MAJOR)
    assertEquals(keepLastMajor.events.values.size, 2)
  }
  
  test("mergeWith applies strategy correctly when merging") {
    val temp = AnswerChangedEvent(1, time.plusSeconds(5), UpdateImportance.TEMPORARY)
    val major = AnswerChangedEvent(2, time.plusSeconds(10), UpdateImportance.MAJOR)
    
    val history1 = InteractionVariableHistory.empty[Int, Int, Int].addEvent(temp).addEvent(major)
    val history2 = InteractionVariableHistory.empty[Int, Int, Int].addEvent(major)
    
    val merged = history1.mergeWith(history2)
    assertEquals(merged.events.values.size, 2)
    assertEquals(merged.events.values.count(_.updateImportance == UpdateImportance.MAJOR), 1)
  }
}
