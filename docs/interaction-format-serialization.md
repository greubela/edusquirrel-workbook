# InteractionFormat Serialization Design

## Overview

This document explains the design decisions behind making `InteractionFormat` serializable in the edusquirrel-workbook project.

## Why Case Objects for Formats?

`InteractionFormat` is implemented as a **sealed trait** where each interaction type has a **case object** that extends it:

```scala
sealed trait InteractionFormat[T, GA, SA] derives ReadWriter {
  def stateSerializer: Serializer[T]
  def historySerializer: Serializer[InteractionVariableHistory[T, GA, SA]]
  def exerciseSerializer: Serializer[WorkbookInteractionElementFull[T, GA, SA]]
}

object TextInteraction extends InteractionFormat[String, Unit, Unit] {
  override def stateSerializer: Serializer[String] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[String, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[String, Unit, Unit]] = Serializer.fromImplicitRW
}
```

### Benefits of This Approach

1. **Naturally Serializable**: Case objects are singletons. When serialized, only the object's fully qualified name is stored. When deserialized, Scala returns the same singleton instance.

2. **No Serialization Issues**: The `Serializer` fields inside the object don't need to be serializable because:
   - The case object itself serializes as just a reference (its name)
   - Serializers are lazily evaluated or recreated when needed
   - No instance state is serialized

3. **Type Safety**: Each interaction type has exactly one format instance, enforced by the compiler.

4. **Cross-Server Portability**: Format instances can be passed as method parameters across server boundaries without complex registry mechanisms.

5. **Simplicity**: Just extend the trait - no additional boilerplate or configuration needed.

## How It Works

### Serialization

When you serialize a case object that extends `InteractionFormat`:

1. The serializer stores only the fully qualified class name (e.g., `"it.evadid.workbook.elements.interactionElements.basic.LabeledCheckboxInteraction"`)
2. No instance data is serialized (including the serializers)

### Deserialization

When you deserialize:

1. Scala looks up the case object by its fully qualified name
2. Returns the same singleton instance
3. The instance has all the required serializer implementations

### Example

```scala
// Create format instance
val format: InteractionFormat[String, Unit, Unit] = TextInteraction

// Serialize - only stores the class name
val json = write(format)

// Deserialize - returns the same TextInteraction object
val restoredFormat: InteractionFormat[String, Unit, Unit] = read(json)
assert(restoredFormat eq TextInteraction)  // True - same instance!
```

## Usage Pattern

### In Interaction Definitions

```scala
// Interaction class - does NOT extend InteractionFormat
case class TextInteraction(...) extends WorkbookInteractionElementFull[String, Unit, Unit] {
  override def format: InteractionFormat[String, Unit, Unit] = TextInteraction
}

// Companion object - IS the format instance
object TextInteraction extends InteractionFormat[String, Unit, Unit] {
  // Implement the three required serializers
  override def stateSerializer: Serializer[String] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[String, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[String, Unit, Unit]] = Serializer.fromImplicitRW
}
```

### In Backend Methods

```scala
// Format is passed explicitly
def processHistory(format: InteractionFormat[State, Grade, Scaffold], json: String): History = {
  format.historySerializer.deserialize(json)
}

// Usage - pass the companion object
processHistory(TextInteraction, historyJson)
```

## Comparison with Alternatives

### Registry-Based Approach

```scala
// OLD (registry-based)
def getFormat(elementId: String): InteractionFormat[?, ?, ?] = {
  val format = InteractionFormatRegistry.get(elementId)
  // Runtime lookup, not type-safe
}
```

**Problems**:
- Needs registry on every server
- Runtime type inference
- Cross-server portability issues

### Anonymous Class Approach

```scala
// Create anonymous instance
val format = InteractionFormat.fromReadWriters[String, Unit, Unit]
```

**Problems**:
- New instance each time
- Not naturally serializable (serializers inside need to be serializable)

### Case Object Approach (Current)

```scala
// Companion object IS the format
object TextInteraction extends InteractionFormat[String, Unit, Unit]
```

**Benefits**:
- Single instance (natural singleton)
- Naturally serializable
- Type-safe
- No registry needed

## SessionData Integration

`SessionData` uses `InteractionHistoryJson` to store format and history together:

```scala
case class SessionData(
  timestampStorage: LocalDateTime,
  knownUsageData: Map[SyncContext, InteractionHistoryJson[?, ?, ?]],
  userInfo: User,
  userConfig: Option[UserConfig],
  workbookInfo: AllWorkbookInfo
) derives ReadWriter

case class InteractionHistoryJson[T, GA, SA](
  format: InteractionFormat[T, GA, SA],  // The format instance (case object)
  historyJson: String                     // The serialized history
) derives ReadWriter
```

This ensures:
- Format is stored alongside history for deserialization
- Format is a case object, naturally serializable
- History JSON is stored as a string for database compatibility

## Timestamp Precision

`LocalDateTime` in Scala supports nanosecond precision (9 fractional digits), which is more than sufficient for all use cases. The timestamp format used is:

```
yyyy-MM-ddTHH:mm:ss.nnnnnnnnn
```

This ensures:
- All timestamps have at least nanosecond precision
- `LocalDateTime` can represent any timestamp in this format without loss
- Re-serialization produces identical JSON when using the same format

The database stores timestamps as `VARCHAR(40)` with the full ISO format, preserving exact string representation. When fetched and re-serialized, events are recognized as identical because the same string format is used.

## Database Storage

The database **does NOT store format information**. Instead:

1. **Database stores raw JSON strings** in the `answer_event.serialized_data`, `grading_event.serialized_data`, and `scaffolding_event.serialized_data` columns

2. **Format is looked up from the interaction definition** when needed:
   - The `variable_id` in `sync_context_id` identifies the interaction
   - The interaction's companion object provides the format instance
   - `InteractionHistoryJson` wraps the format + JSON for client-server communication

3. **Why this design**:
   - Database is a simple storage layer - only data, no metadata
   - Format is application metadata (which interaction type)
   - The client/server always has interaction definitions loaded
   - Separates concerns: storage (database) vs. type info (code)

This is the correct separation of concerns for the edusquirrel-workbook architecture.

## Summary

The case object approach to `InteractionFormat` provides:

1. **Naturally serializable** - No custom serialization needed
2. **Type-safe** - Compiler enforces correct format usage
3. **Simple** - Just extend the trait
4. **Cross-server compatible** - Works without registry
5. **Efficient** - Single instance per interaction type

This design solves the core problem: **passing format information across server boundaries without serialization issues or registry dependencies**.
