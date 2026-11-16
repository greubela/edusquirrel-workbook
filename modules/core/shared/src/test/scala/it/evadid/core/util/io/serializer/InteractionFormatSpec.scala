package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import upickle.default.*
import munit.FunSuite

class InteractionFormatSpec extends FunSuite {

  test("InteractionFormat can be created from serializers") {
    val stateSer: Serializer[String] = Serializer.fromImplicitRW
    val historySer: Serializer[InteractionVariableHistory[String, Unit, Unit]] = Serializer.fromImplicitRW
    val exerciseSer: Serializer[WorkbookInteractionElementFull[String, Unit, Unit]] = Serializer.fromImplicitRW

    val format = InteractionFormat(stateSer, historySer, exerciseSer)

    assert(format.stateSerializer eq stateSer)
    assert(format.historySerializer eq historySer)
    assert(format.exerciseSerializer eq exerciseSer)
  }

  test("InteractionFormat can be created from ReadWriters") {
    implicit val rwString: ReadWriter[String] = macroRW
    implicit val rwUnit: ReadWriter[Unit] = macroRW

    val format = InteractionFormat.fromReadWriters[String, Unit, Unit]

    // Verify the format can serialize and deserialize
    val history = InteractionVariableHistory.empty[String, Unit, Unit]
    val serialized = format.historySerializer.serialize(history)
    val deserialized = format.historySerializer.deserialize(serialized)

    assertEquals(deserialized, history)
  }

  test("TextInteraction format is registered") {
    // TextInteraction registers "TextInteraction" as its element ID
    val formatOpt = InteractionFormat.get[String, Unit, Unit]("TextInteraction")
    assert(formatOpt.isDefined)
    
    val format = formatOpt.get
    // Verify we can serialize/deserialize a history
    val history = InteractionVariableHistory.empty[String, Unit, Unit]
    val serialized = format.historySerializer.serialize(history)
    val deserialized = format.historySerializer.deserialize(serialized)
    
    assertEquals(deserialized, history)
  }

  test("Multiple formats can be registered and retrieved") {
    // Register a custom format
    InteractionFormat.registerWithSerializers(
      elementIdPrefix = "TestInteraction",
      stateSerializer = Serializer.fromImplicitRW,
      historySerializer = Serializer.fromImplicitRW,
      exerciseSerializer = Serializer.fromImplicitRW
    )
    
    // Verify it was registered
    val formatOpt = InteractionFormat.get[Int, String, Unit]("TestInteraction")
    assert(formatOpt.isDefined)
    
    // Clean up - remove the test format
    registry.remove("TestInteraction")
  }

  test("Registered format can serialize and deserialize") {
    // Register a format with custom types
    case class CustomState(value: String)
    case class CustomGrade(score: Int)
    case class CustomScaffold(hint: String)
    
    given ReadWriter[CustomState] = macroRW
    given ReadWriter[CustomGrade] = macroRW
    given ReadWriter[CustomScaffold] = macroRW
    given ReadWriter[InteractionVariableHistory[CustomState, CustomGrade, CustomScaffold]] = macroRW
    
    val format = InteractionFormat.fromReadWriters[CustomState, CustomGrade, CustomScaffold]
    
    // Test with actual data
    val state = CustomState("test")
    val history = InteractionVariableHistory.empty[CustomState, CustomGrade, CustomScaffold].addState(state)
    
    val serialized = format.historySerializer.serialize(history)
    val deserialized = format.historySerializer.deserialize(serialized)
    
    assertEquals(deserialized.historyStates.lastStateOption.map(_.value.value), Some("test"))
  }

  test("InteractionFormat registeredIds includes registered formats") {
    // After registration, the ID should appear in registeredIds
    InteractionFormat.register[String, Unit, Unit](elementIdPrefix = "TempInteraction")
    
    assert(InteractionFormat.registeredIds.contains("TempInteraction"))
    
    // Clean up
    registry.remove("TempInteraction")
  }

  test("FormatProvider provides type-safe format access") {
    // Register a format
    InteractionFormat.register[String, Unit, Unit](elementIdPrefix = "ProviderTest")
    
    // Use FormatProvider to get a type-safe format
    val provider = InteractionFormat.FormatProvider[String, Unit, Unit]
    val format = provider.get("ProviderTest")
    
    // Verify it works
    val history = InteractionVariableHistory.empty[String, Unit, Unit]
    val serialized = format.historySerializer.serialize(history)
    val deserialized = format.historySerializer.deserialize(serialized)
    
    assertEquals(deserialized, history)
    
    // Clean up
    registry.remove("ProviderTest")
  }
  
  test("FormatProvider throws for unknown element ID") {
    val provider = InteractionFormat.FormatProvider[String, Unit, Unit]
    
    intercept[NoSuchElementException] {
      provider.get("NonExistent")
    }
  }

}
