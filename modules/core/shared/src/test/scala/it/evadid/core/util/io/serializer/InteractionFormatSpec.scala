package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{InteractionFormat, TextInteractionFormat}
import it.evadid.workbook.elements.interactionElements.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, MessagingInteraction, TextInteraction}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleRecreateShapeInteraction, TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.elements.interactionElements.blockchain.BlockchainInteraction
import it.evadid.workbook.elements.interactionElements.evacuation.EvacuationSimulationInteraction
import it.evadid.workbook.elements.interactionElements.sql.SqlCommandExercise
import it.evadid.workbook.elements.interactionElements.sorting.sortingExercise.SortingInteraction
import it.evadid.workbook.elements.interactionElements.table.AnswerTableInteraction
import upickle.default.*
import munit.FunSuite

class InteractionFormatSpec extends FunSuite {

  // ============================================================================
  // Basic format creation
  // ============================================================================

  test("InteractionFormat from known formats works") {
    // Verify TextInteractionFormat is accessible
    assert(TextInteractionFormat != null)
  }

  // ============================================================================
  // Format serializers are independent
  // ============================================================================

  test("Format stateSerializer serializes state correctly") {
    val testValue = "hello world"
    val serialized = TextInteractionFormat.stateSerializer.serialize(testValue)
    val deserialized = TextInteractionFormat.stateSerializer.deserialize(serialized)
    assert(deserialized == testValue)
  }

  test("Format historySerializer handles empty history") {
    val history = InteractionVariableHistory.empty[TextInteractionFormat.type]

    val serialized = TextInteractionFormat.historySerializer.serialize(history)
    val deserialized = TextInteractionFormat.historySerializer.deserialize(serialized)
    assert(deserialized == history)
  }

  test("Format historySerializer handles history with states") {
    val state1 = InteractionVariableState("first", UpdateImportance.MAJOR, java.time.LocalDateTime.now())
    val state2 = InteractionVariableState("second", UpdateImportance.MINOR, java.time.LocalDateTime.now())

    val history = InteractionVariableHistory.empty[TextInteractionFormat.type]
      .addState(state1)
      .addState(state2)

    val serialized = TextInteractionFormat.historySerializer.serialize(history)
    val deserialized = TextInteractionFormat.historySerializer.deserialize(serialized)

    assert(deserialized.historyStates.historyStates.size == 2)
    assert(deserialized.historyStates.historyStates(0).value == "first")
    assert(deserialized.historyStates.historyStates(1).value == "second")
  }

  // ============================================================================
  // TextInteraction format
  // ============================================================================

  test("TextInteraction format is accessible") {
    val format = TextInteraction.format
    assert(format != null)
  }

  test("TextInteraction format is the same on repeated calls") {
    val format1 = TextInteraction.format
    val format2 = TextInteraction.format
    assert(format1 eq format2)  // Same instance (lazy val)
  }

  test("TextInteraction format can serialize/deserialize history") {
    val format = TextInteraction.format
    val history = InteractionVariableHistory.empty[TextInteractionFormat.type]
      .addState(InteractionVariableState("code", UpdateImportance.MAJOR, java.time.LocalDateTime.now()))

    val serialized = format.historySerializer.serialize(history)
    val deserialized = format.historySerializer.deserialize(serialized)
    assert(deserialized.historyStates.lastStateOption.map(_.value) == Some("code"))
  }

  test("TextInteraction format stateSerializer can serialize/deserialize") {
    val format = TextInteraction.format
    val testValue = "hello world"
    val serialized = format.stateSerializer.serialize(testValue)
    val deserialized = format.stateSerializer.deserialize(serialized)
    assert(deserialized == testValue)
  }

  test("TextInteraction format exerciseSerializer can serialize interaction") {
    val format = TextInteraction.format
    val interaction = TextInteraction("test-id")
    val serialized = format.exerciseSerializer.serialize(interaction)
    val deserialized = format.exerciseSerializer.deserialize(serialized)
    assert(deserialized.elementId == "test-id")
  }

  // ============================================================================
  // Format can be passed as method parameter (key feature)
  // ============================================================================

  test("Format can be passed as explicit parameter to backend function") {
    def processHistory(format: TextInteractionFormat.type, json: String): String = {
      val history = format.historySerializer.deserialize(json)
      format.stateSerializer.serialize(history.historyStates.lastStateOption.map(_.value).getOrElse(""))
    }

    val history = InteractionVariableHistory.empty[TextInteractionFormat.type]
      .addState(InteractionVariableState("test", UpdateImportance.MAJOR, java.time.LocalDateTime.now()))
    val json = TextInteraction.format.historySerializer.serialize(history)

    val result = processHistory(TextInteractionFormat, json)
    assert(result == "\"test\"")
  }

  test("Format works with different interaction types") {
    // Verify multiple formats can coexist without registry interference
    val textFormat = TextInteraction.format
    val turtleFormat = TurtleRecreateShapeInteraction.format

    // Text format works
    val textHistory = InteractionVariableHistory.empty[TextInteractionFormat.type]
    val textSerialized = textFormat.historySerializer.serialize(textHistory)
    val textDeserialized = textFormat.historySerializer.deserialize(textSerialized)
    assert(textDeserialized == textHistory)

    // Turtle format works
    val turtleHistory = InteractionVariableHistory.empty[TurtleRecreateShapeInteraction.format.F.type]
    val turtleSerialized = turtleFormat.historySerializer.serialize(turtleHistory)
    val turtleDeserialized = turtleFormat.historySerializer.deserialize(turtleSerialized)
    assert(turtleDeserialized == turtleHistory)
  }

  // ============================================================================
  // TurtleRecreateShapeInteraction format
  // ============================================================================

  test("TurtleRecreateShapeInteraction format is accessible") {
    val format = TurtleRecreateShapeInteraction.format
    assert(format != null)
  }

  test("TurtleRecreateShapeInteraction format can serialize empty history") {
    val format = TurtleRecreateShapeInteraction.format
    val history = InteractionVariableHistory.empty[TurtleRecreateShapeInteraction.format.F.type]
    val serialized = format.historySerializer.serialize(history)
    val deserialized = format.historySerializer.deserialize(serialized)
    assert(deserialized == history)
  }

  test("TurtleRecreateShapeInteraction format exerciseSerializer works") {
    val format = TurtleRecreateShapeInteraction.format

    val initProgram = ProgrammingState("", Map.empty, Map.empty)
    val graphic = TurtleGraphic.TurtleGraphicProgram(Seq.empty)
    
    val interaction = TurtleRecreateShapeInteraction(
      elementId = "turtle-1",
      initProgram = initProgram,
      desiredResult = graphic,
      comparisonPolicy = TurtleDrawingPolicy.Strokes,
      customExerciseConfig = None
    )

    val serialized = format.exerciseSerializer.serialize(interaction)
    val deserialized = format.exerciseSerializer.deserialize(serialized)
    
    assert(deserialized.elementId == "turtle-1")
    assert(deserialized.initProgram.code == "")
  }

  // ============================================================================
  // Other interactions (verify format is accessible)
  // ============================================================================

  test("ChoiceInteraction format is accessible") {
    val format = ChoiceInteraction.format
    assert(format != null)
  }

  test("LabeledCheckboxInteraction format is accessible") {
    val format = LabeledCheckboxInteraction.format
    assert(format != null)
  }

  test("LabeledNumberInteraction format is accessible") {
    val format = LabeledNumberInteraction.format
    assert(format != null)
  }

  test("MessagingInteraction format is accessible") {
    val format = MessagingInteraction.format
    assert(format != null)
  }

  test("SortingInteraction format is accessible") {
    val format = SortingInteraction.format
    assert(format != null)
  }

  test("AnswerTableInteraction format is accessible") {
    val format = AnswerTableInteraction.format
    assert(format != null)
  }

  test("BlockchainInteraction format is accessible") {
    val format = BlockchainInteraction.format
    assert(format != null)
  }

  test("EvacuationSimulationInteraction format is accessible") {
    val format = EvacuationSimulationInteraction.format
    assert(format != null)
  }

  test("SqlCommandExercise format is accessible") {
    val format = SqlCommandExercise.format
    assert(format != null)
  }

  // ============================================================================
  // Format immutability and purity
  // ============================================================================

  test("Format stateSerializer is pure (same input -> same output)") {
    val input = "test"
    val output1 = TextInteractionFormat.stateSerializer.serialize(input)
    val output2 = TextInteractionFormat.stateSerializer.serialize(input)
    assert(output1 == output2)
  }

  test("Format historySerializer is pure") {
    val history = InteractionVariableHistory.empty[TextInteractionFormat.type]
    val output1 = TextInteractionFormat.historySerializer.serialize(history)
    val output2 = TextInteractionFormat.historySerializer.serialize(history)
    assert(output1 == output2)
  }

  test("Format serializers don't mutate state") {
    val originalState = TextInteractionFormat.stateSerializer.serialize("test")

    // Multiple calls shouldn't change anything
    val state1 = TextInteractionFormat.stateSerializer.serialize("test")
    val state2 = TextInteractionFormat.stateSerializer.serialize("test")

    assert(state1 == originalState)
    assert(state2 == originalState)
  }

  // ============================================================================
  // Format type safety
  // ============================================================================

  test("Format maintains type safety across serializers") {
    val format = TextInteraction.format

    // State serializer must work with String
    val state: String = "test"
    val serializedState = format.stateSerializer.serialize(state)
    val deserializedState: String = format.stateSerializer.deserialize(serializedState)
    assert(deserializedState == state)
  }

}
