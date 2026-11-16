# InteractionFormat: Type-Safe Serialization for Backend Interactions

## Overview

`InteractionFormat` is a **sealed trait** providing type-level association between an interaction type and its serializers. Each interaction has exactly one format instance (a case object that extends the trait), naturally serializable as a singleton.

## Design

```scala
sealed trait InteractionFormat[T, GA, SA] derives ReadWriter {
  def stateSerializer: Serializer[T]
  def historySerializer: Serializer[InteractionVariableHistory[T, GA, SA]]
  def exerciseSerializer: Serializer[WorkbookInteractionElementFull[T, GA, SA]]
}
```

### Key Design Decisions

1. **Sealed trait** - All implementations known at compile time
2. **Case objects** - Naturally serializable singletons
3. **No registry** - Formats passed explicitly through method signatures
4. **Type-safe** - Compiler enforces format consistency
5. **Strong typing** - `T, GA, SA` type parameters for state, grading, scaffolding

## Usage

### In Interaction Definitions

```scala
case class TextInteraction(elementId: String) 
  extends WorkbookInteractionElementFull[String, Unit, Unit] {
  
  override def format: InteractionFormat[String, Unit, Unit] = TextInteraction
}

object TextInteraction extends InteractionFormat[String, Unit, Unit] {
  override def stateSerializer: Serializer[String] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[...] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[...] = Serializer.fromImplicitRW
}
```

### Using Format on the Backend

```scala
def processHistory(format: InteractionFormat[State, Grade, Scaffold], json: String): History = {
  format.historySerializer.deserialize(json)
}

// Pass the companion object (which IS the format)
processHistory(TextInteraction, json)
```

## Why This Approach

| Benefit | Explanation |
|---------|-------------|
| **Strong typing** | Compile-time type safety via `[T, GA, SA]` type parameters |
| **Auto-serialization** | Case objects serialize by class name automatically |
| **No registry** | Format passed explicitly, no lookup needed |
| **Portable** | Works across server boundaries |
| **Type-safe operations** | No unsafe casting; all operations use typed format |

## Integration

### Database Sync

```scala
def fetchHistory(format: InteractionFormat[State, Grade, Scaffold], elementId: String): History = {
  val json = db.query("SELECT history FROM sync_data WHERE element_id = ?", elementId)
  format.historySerializer.deserialize(json)
}
```

### Backend Server

```scala
case class StoreCommand[State, Grade, Scaffold](
  format: InteractionFormat[State, Grade, Scaffold],
  elementId: String,
  historyJson: String
) {
  def execute: SyncSuccess = {
    val history = format.historySerializer.deserialize(historyJson)
    // Process and store
  }
}
```

## Format Storage

All known formats are stored in `InteractionFormat.knownFormats`:

```scala
val knownFormats: Set[InteractionFormat[?, ?, ?]] = Set(
  TextInteraction,
  ChoiceInteraction,
  // ... all other known formats
)
```

This enables:
- Type-safe format lookup
- Compile-time verification of known formats
- Clear error messages for unknown formats

## Serialization

- Format is serialized as the fully qualified class name string
- Deserialization loads the singleton case object
- All formats must be compile-time known (in `InteractionFormat.knownFormats`)
- Unknown formats throw descriptive error

## Examples in Codebase

- `TextInteraction`: Simple string-based state
- `ChoiceInteraction`: Choice selection state
- `TurtleRecreateShapeInteraction`: Complex programming state with grading

## Migration

1. Interaction class extends `WorkbookInteractionElementFull[T, GA, SA]` (NOT `InteractionFormat`)
2. Add `override def format: InteractionFormat[T, GA, SA] = CompanionObject`
3. Companion object extends `InteractionFormat[T, GA, SA]`
4. Implement three serializer methods (use `Serializer.fromImplicitRW`)

## See Also

- [`interaction-format-serialization.md`](interaction-format-serialization.md) - Detailed explanation of the sealed trait + case object design
- [`session-data-serialization.md`](session-data-serialization.md) - How SessionData uses InteractionHistoryJson for storing format with history
