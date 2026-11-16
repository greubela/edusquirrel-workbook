package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.interaction.sync.UpdateImportance
import upickle.default.*
import munit.FunSuite

class InteractionFormatSpec extends FunSuite {

  // ============================================================================
  // Basic format creation
  // ============================================================================

  test("InteractionFormat.fromReadWriters creates format from ReadWriters") {
    val format = InteractionFormat.fromReadWriters[String, Unit, Unit]

    // Verify all serializers are available
    val serializedState = format.stateSerializer.serialize("test")
    assert(serializedState == "\"test\"")

    val history = InteractionVariableHistory.empty[String, Unit, Unit]
    val serialized = format.historySerializer.serialize(history)
    assert(format.historySerializer.deserialize(serialized) == history)
  }

  test("InteractionFormat.apply creates format from individual serializers") {
    val stateSer: Serializer[String] = Serializer.fromImplicitRW
    val historySer: Serializer[InteractionVariableHistory[String, Unit, Unit]] = Serializer.fromImplicitRW
    val exerciseSer: Serializer[WorkbookInteractionElementFull[String, Unit, Unit]] = Serializer.fromImplicitRW

    val format = InteractionFormat(stateSer, historySer, exerciseSer)

    assert(format.stateSerializer eq stateSer)
    assert(format.historySerializer eq historySer)
    assert(format.exerciseSerializer eq exerciseSer)
  }

  // ============================================================================
  // TextInteraction format
  // ============================================================================

  test("TextInteraction format is accessible") {
    val format = TextInteraction.format
    assert(format != null)
  }

  test("TextInteraction format can serialize/deserialize history") {
    val format = TextInteraction.format
    val history = InteractionVariableHistory.empty[String, Unit, Unit]
    val serialized = format.historySerializer.serialize(history)
    assert(format.historySerializer.deserialize(serialized) == history)
  }

  test("TextInteraction format stateSerializer can serialize/deserialize") {
    val format = TextInteraction.format
    val testValue = "hello world"
    val serialized = format.stateSerializer.serialize(testValue)
    assert(format.stateSerializer.deserialize(serialized) == testValue)
  }

  test("TextInteraction format exerciseSerializer can serialize interaction") {
    val format = TextInteraction.format
    val interaction = TextInteraction("test-id")
    val serialized = format.exerciseSerializer.serialize(interaction)
    val deserialized = format.exerciseSerializer.deserialize(serialized)
    assert(deserialized.elementId == "test-id")
  }

  // ============================================================================
  // History serialization
  // ============================================================================

  test("Format historySerializer handles empty history") {
    val format = InteractionFormat.fromReadWriters[String, Unit, Unit]
    val history = InteractionVariableHistory.empty[String, Unit, Unit]

    val serialized = format.historySerializer.serialize(history)
    assert(format.historySerializer.deserialize(serialized) == history)
  }

  test("Format historySerializer handles history with states") {
    val format = InteractionFormat.fromReadWriters[String, Unit, Unit]

    val history = InteractionVariableHistory.empty[String, Unit, Unit]
      .addState(InteractionVariableState("first", UpdateImportance.MAJOR, java.time.LocalDateTime.now()))
      .addState(InteractionVariableState("second", UpdateImportance.MAJOR, java.time.LocalDateTime.now()))

    val serialized = format.historySerializer.serialize(history)
    val deserialized = format.historySerializer.deserialize(serialized)

    assert(deserialized.historyStates.lastStateOption.map(_.value) == Some("second"))
  }

}
