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
    // For Scala 3, use given instances for standard types that already have ReadWriter
    import it.evadid.core.util.io.serializer.DefaultSerializer.given
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
    // Register a format with types that derive ReadWriter
    import it.evadid.core.util.io.serializer.DefaultSerializer.given
    val format = InteractionFormat.fromReadWriters[String, String, String]
    InteractionFormat.registerWithSerializers(
      "TestInteraction",
      format.stateSerializer,
      format.historySerializer,
      format.exerciseSerializer
    )
    
    // Verify it was registered
    val formatOpt = InteractionFormat.get[String, String, String]("TestInteraction")
    assert(formatOpt.isDefined)
    
    // Clean up - remove the test format
    InteractionFormat.clearRegistry()
  }

  test("Registered format can serialize and deserialize") {
    // Register a format with custom types
    case class CustomState(value: String) derives ReadWriter
    case class CustomGrade(score: Int) derives ReadWriter
    case class CustomScaffold(hint: String) derives ReadWriter
    
    val format = InteractionFormat.fromReadWriters[CustomState, CustomGrade, CustomScaffold]
    
    // Test with actual data - use InteractionVariableState with proper imports
    val state = it.evadid.workbook.interaction.variable.InteractionVariableState(CustomState("test"), it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR, java.time.LocalDateTime.now())
    val history = InteractionVariableHistory.empty[CustomState, CustomGrade, CustomScaffold].addState(state)
    
    val serialized = format.historySerializer.serialize(history)
    val deserialized = format.historySerializer.deserialize(serialized)
    
    assertEquals(deserialized.historyStates.lastStateOption.map(_.value.value), Some("test"))
  }

  test("InteractionFormat registeredIds includes registered formats") {
    // After registration, the ID should appear in registeredIds
    InteractionFormat.register[String, Unit, Unit]("TempInteraction")
    
    assert(InteractionFormat.registeredIds.contains("TempInteraction"))
    
    // Clean up
    InteractionFormat.clearRegistry()
  }

  test("FormatProvider provides type-safe format access") {
    // Register a format
    InteractionFormat.register[String, Unit, Unit]("ProviderTest")
    
    // Use FormatProvider to get a type-safe format
    val provider = InteractionFormat.FormatProvider[String, Unit, Unit]
    val format = provider.get("ProviderTest")
    
    // Verify it works
    val history = InteractionVariableHistory.empty[String, Unit, Unit]
    val serialized = format.historySerializer.serialize(history)
    val deserialized = format.historySerializer.deserialize(serialized)
    
    assertEquals(deserialized, history)
    
    // Clean up
    InteractionFormat.clearRegistry()
  }
  
  test("FormatProvider throws for unknown element ID") {
    val provider = InteractionFormat.FormatProvider[String, Unit, Unit]
    
    intercept[NoSuchElementException] {
      provider.get("NonExistent")
    }
  }

}
