# InteractionFormat: Type-Safe Serialization for Backend Interactions

## Overview

`InteractionFormat` is a trait that provides a **type-level association** between an interaction type and its serializers. This enables backend-side serialization/deserialization of interaction histories without requiring runtime access to interaction instances.

## Problem Statement

Previously, serialization on the backend server (e.g., for sync operations) required:
- Runtime access to `InteractionVariable` instances
- Type inference from available interaction instances
- Complex type handling when no interaction variables were available

This was problematic for:
- Backend services that only receive serialized data
- Historical data processing
- Server-side batch operations
- Cross-server synchronization

## Solution

The `InteractionFormat` trait provides:
```scala
trait InteractionFormat[T, GA, SA] {
  def stateSerializer: Serializer[T]
  def historySerializer: Serializer[InteractionVariableHistory[T, GA, SA]]
  def exerciseSerializer: Serializer[WorkbookInteractionElementFull[T, GA, SA]]
}
```

### Key Design Decisions

1. **No Registry** - Formats are passed explicitly through method signatures
2. **Serializable Object** - Format itself can be serialized and passed across server boundaries
3. **Type-Safe** - Compiler enforces format consistency
4. **Explicit in Signatures** - Method parameters declare their format requirements

## Usage

### 1. In Interaction Definitions

```scala
// Define your interaction with format override
case class MyInteraction(
  override val elementId: String,
  config: String
) extends WorkbookInteractionElementFull[MyState, MyGrade, MyScaffold] {
  
  override def format: InteractionFormat[MyState, MyGrade, MyScaffold] = MyInteraction.format
}

// In the companion object, extend InteractionFormat
object MyInteraction extends InteractionFormat[MyState, MyGrade, MyScaffold] {
  // Implement serializers (typically using fromReadWriters)
}
```

### 2. Using Format on the Backend

```scala
// Format is passed explicitly, no registry lookup needed
def processHistory(format: InteractionFormat[State, Grade, Scaffold], json: String): History = {
  format.historySerializer.deserialize(json)
}

// Pass format to backend methods
def handleSyncElement(format: InteractionFormat[State, Grade, Scaffold], elementId: String, json: String): SyncResult = {
  val history = processHistory(format, json)
  // ... process history
}
```

### 3. Direct Format Creation

```scala
// Create format directly with fromReadWriters
val format = InteractionFormat.fromReadWriters[State, Grade, Scaffold]
```

## Integration Points

### Database Sync

```scala
// In database sync handlers - format passed explicitly
def fetchHistory(format: InteractionFormat[State, Grade, Scaffold], elementId: String): Future[InteractionVariableHistory[?, ?, ?]] = {
  val json = db.query("SELECT history FROM sync_data WHERE element_id = ? AND ...", elementId)
  Future.successful(format.historySerializer.deserialize(json))
}

// Usage
fetchHistory(TextInteraction.format, "text-interaction-123")
```

### Backend Server

```scala
// In command handlers - format passed explicitly
case class StoreInteractionCommand[State, Grade, Scaffold](
  format: InteractionFormat[State, Grade, Scaffold],
  elementId: String,
  historyJson: String
) {
  def execute: Future[SyncSuccess] = {
    val history = format.historySerializer.deserialize(historyJson)
    // Process and store
  }
}
```

## Implementation Details

### Type Safety

The implementation uses Scala's type system to ensure:
- All three serializers are always consistent
- Type parameters propagate correctly through the chain
- No unsafe casting is needed in most cases

### Explicit Format Passing

Formats are passed explicitly as method parameters:

```scala
// Before (registry-based - problematic)
def syncElement(elementId: String): Future[History] = {
  val format = InteractionFormat.forElementId(...)  // Needs registry lookup
  format.historySerializer.deserialize(...)
}

// After (explicit - portable)
def syncElement(format: InteractionFormat[State, Grade, Scaffold], elementId: String): Future[History] = {
  format.historySerializer.deserialize(...)  // No registry needed
}
```

### Compatibility

The implementation is fully compatible with existing code:
- Uses existing `Serializer` and `InteractionVariableHistory` types
- Works with `derives ReadWriter` via macro expansion
- No breaking changes to existing APIs
- `WorkbookInteractionElementFull` now has a `def format` method

## Migration Guide

### For Existing Interactions

1. Remove `derives upickle.default.ReadWriter` from the case class
2. Add `override def format` method to return the companion's format
3. Make companion object extend `InteractionFormat`
4. Implement the three serializer methods (use `fromReadWriters`)

Example:
```scala
// Before
case class TextInteraction(...) extends WorkbookInteractionElement[String] derives upickle.default.ReadWriter

object TextInteraction {
  // ...
}

// After
case class TextInteraction(...) extends WorkbookInteractionElement[String] {
  override def format: InteractionFormat[String, Unit, Unit] = TextInteraction.format
}

object TextInteraction extends InteractionFormat[String, Unit, Unit] {
  override def stateSerializer: Serializer[String] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[String, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[String, Unit, Unit]] = Serializer.fromImplicitRW
}
```

### Testing

Test your format:

```scala
class MyInteractionSpec extends FunSuite {
  test("MyInteraction format is accessible") {
    val format = MyInteraction.format
    assert(format != null)
  }
  
  test("Format can serialize/deserialize history") {
    val format = MyInteraction.format
    val history = InteractionVariableHistory.empty[State, Grade, Scaffold].addState(testState)
    val serialized = format.historySerializer.serialize(history)
    val deserialized = format.historySerializer.deserialize(serialized)
    assertEquals(deserialized, history)
  }
}
```

## Best Practices

1. **Always override format**: Each interaction should override `format` to use the companion's format
2. **Pass format explicitly**: Don't rely on registry lookups; pass format as method parameter
3. **Use fromReadWriters**: Default to `fromReadWriters` unless custom serialization is needed
4. **Document custom serializers**: If using custom serializers, explain why

## Examples in Codebase

See these interactions for working examples:

- `TextInteraction`: Simple string-based state (uses `InteractionFormat`)
- `TurtleRecreateShapeInteraction`: Complex programming state with grading (uses `InteractionFormat`)

## Comparison with Registry-Based Approach

| Aspect | Registry-Based | Explicit Format Passing (Implemented) |
|--------|---------------|--------------------------------------|
| Backend lookup | `InteractionFormat.forElementId("id")` | Pass format as method parameter |
| Cross-server portability | ❌ Element ID might not exist | ✅ Format is serializable object |
| Type safety | Runtime check (Option) | Compile-time type check |
| Method signatures | `def sync(elementId: String)` | `def sync(format: Format, elementId: String)` |
| Testability | Needs registry setup | Pass format directly |
| Server boundaries | ❌ Needs registry on each server | ✅ Format passed as data |

### Why Explicit Format Passing?

The explicit format passing approach was chosen because:

1. **Portability** - Formats can be serialized and passed across server boundaries
2. **Type Safety** - Compiler enforces format consistency
3. **Explicit Dependencies** - Method signatures clearly show format requirements
4. **No Registry Needed** - Works on servers that don't know about all element types
5. **Simpler Testing** - Pass format directly without registry setup

## Future Enhancements

Potential improvements to consider:

1. **Compile-time verification**: Use Scala 3 macros to verify format implementation
2. **Format versioning**: Support schema evolution
3. **Lazy loading**: Load formats on-demand rather than at startup
4. **Format discovery**: Auto-discover formats via Scala 3 given instances

## Summary

The `InteractionFormat` trait provides:
- **Type-safe serializers** at compile time
- **Explicit passing** through method signatures
- **Serializable object** that works across server boundaries
- **No registry dependency** - works without knowing all element types upfront

This approach solves the core problem: **serialization without interaction instances or registry lookups**.
