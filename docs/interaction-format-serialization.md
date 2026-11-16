# InteractionFormat Serialization Design

## Overview

`InteractionFormat` is a **strongly typed** trait that provides type-level association between an interaction type and its serializers. Each interaction has exactly one format instance implemented as a case object (the companion object of the interaction class).

## Design Principles

### 1. Strong Typing
`InteractionFormat` is strongly typed with type parameters `[T, GA, SA]`:
- `T`: The interaction state type (e.g., `String`, `ProgrammingState`)
- `GA`: The grading result type (typically `Unit` for no grading)
- `SA`: The scaffolding result type (typically `Unit` for no scaffolding)

This ensures:
- Type-safe serialization/deserialization
- Compile-time checking of format compatibility
- No runtime type errors when handling history data

### 2. Automatically Serializable
`InteractionFormat` is automatically serializable because:
- Implementations are case objects (singletons)
- Case objects are naturally serializable (only class name is stored)
- `ReadWriter` is derived automatically for case objects

### 3. Format is Serializable (Critical Requirement)
`InteractionFormat` MUST be serializable because:
- Format instances are passed from client to server as part of `InteractionHistoryJson`
- Custom workbooks may use interaction formats not known to the server
- The format is needed on the server to deserialize the history JSON

### 4. Compile-Time Known Formats
All interaction formats are known at compile time:
- The client has all interaction definitions loaded
- Format is passed explicitly from client to server
- If an unknown format is received, a descriptive error is thrown

### 5. Serialization Format
Format is serialized as a string containing the fully qualified class name:
```scala
// Serialized format (class name string)
"it.evadid.workbook.elements.interactionElements.basic.TextInteraction"
```

### 6. Strongly Typed Deserialization
When deserializing:
1. Client sends `InteractionHistoryJson(format, json)` with strongly typed format
2. Server receives and deserializes format with correct type parameters
3. Server loads format via `Class.forName(className).MODULE$`
4. Result is strongly typed as `InteractionFormat[T, GA, SA]`
5. If format class isn't available, throws descriptive error

## Usage in Interaction Definitions

```scala
// Interaction class - references companion as format
case class TextInteraction(override val elementId: String) 
  extends WorkbookInteractionElement[String] {
  override def format: InteractionFormat[String, Unit, Unit] = TextInteraction
}

// Companion object - IS the format instance (case object)
object TextInteraction extends InteractionFormat[String, Unit, Unit] {
  override def stateSerializer: Serializer[String] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[String, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[String, Unit, Unit]] = Serializer.fromImplicitRW
}
```

## Database Storage

The format is stored alongside the JSON history:
```sql
CREATE TABLE `single_blob_history` (
  `format` VARCHAR(255) NOT NULL,  -- Fully qualified class name
  `history_json` JSON NOT NULL,
  ...
)
```

## Strong Typing Benefits

1. **Type-Safe Serialization**: The format's serializers are typed to the interaction's types
2. **No Runtime Type Casting**: Deserialization produces correctly typed format
3. **Compile-Time Safety**: Type errors are caught at compile time, not runtime
4. **Clear Contracts**: Each interaction has exactly one format with clear type parameters

## Example Flow

```scala
// Client side - strongly typed format
val format: InteractionFormat[String, Unit, Unit] = TextInteraction
val history = InteractionVariableHistory(...)
val json = InteractionHistoryJson(format, format.historySerializer.serialize(history))

// Server side - format is strongly typed
val received: InteractionHistoryJson[String, Unit, Unit] = deserialize(json)
val format: InteractionFormat[String, Unit, Unit] = received.format  // Strongly typed!
val history: InteractionVariableHistory[String, Unit, Unit] = received.toTyped
```

## Error Handling

If an unknown format class name is received:
```scala
// Error message
Format class 'com.example.UnknownInteraction' is not known. 
This may indicate an unknown interaction type. 
Error: ...
```

This provides:
- Clear indication of missing interaction definitions
- No arbitrary code execution risk
- Strong typing maintained throughout

## Summary

- **Strongly typed**: `InteractionFormat[T, GA, SA]` with compile-time type safety
- **Automatically serializable**: Case objects serialize by class name
- **All formats known at compile time**: Client has definitions, server validates
- **Type-safe operations**: No unsafe casting, all operations use typed format
- **Clear error messages**: Unknown formats fail with descriptive error
