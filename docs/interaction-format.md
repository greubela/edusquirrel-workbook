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

// In the companion object, extend InteractionFormat.Provider
object MyInteraction extends InteractionFormat.Provider[MyState, MyGrade, MyScaffold] {
  
  override def elementIdPrefix: String = "MyInteraction"
  
  // format is automatically provided by Provider trait
}
```

### 2. Using Format on the Backend

```scala
// Retrieve format by element ID
InteractionFormat.forElementId[State, Grade, Scaffold]("MyInteraction") match {
  case Some(format) =>
    // Use format.historySerializer.deserialize(serializedHistory)
    val history = format.historySerializer.deserialize(historyJson)
  case None =>
    // Handle unknown interaction type
}

// Get all registered element IDs
val allIds = InteractionFormat.registeredIds
```

### 3. Direct Format Creation (No Registry)

```scala
// Create format directly without registry
val format = InteractionFormat.fromReadWriters[State, Grade, Scaffold]
```

## Integration Points

### Database Sync

```scala
// In database sync handlers
def fetchHistory(elementId: String, context: UsageContext): Future[InteractionVariableHistory[?, ?, ?]] = {
  val json = db.query("SELECT history FROM sync_data WHERE element_id = ? AND ...", elementId)
  InteractionFormat.forElementId[State, Grade, Scaffold](elementId) match {
    case Some(format) =>
      Future.successful(format.historySerializer.deserialize(json))
    case None =>
      Future.failed(new IllegalStateException(s"No format for $elementId"))
  }
}
```

### Backend Server

```scala
// In command handlers
case class StoreInteractionCommand(elementId: String, historyJson: String) {
  def execute: Future[SyncSuccess] = {
    InteractionFormat.forElementId[State, Grade, Scaffold](elementId) match {
      case Some(format) =>
        val history = format.historySerializer.deserialize(historyJson)
        // Process and store
      case None =>
        Future.failed(new IllegalArgumentException(s"Unknown elementId: $elementId"))
    }
  }
}
```

## Implementation Details

### Type Safety

The implementation uses Scala's type system to ensure:
- All three serializers are always consistent
- Type parameters are propagated correctly through the chain
- No unsafe casting is needed in most cases

### Provider Pattern

The `Provider` trait provides:
- Automatic format creation with `fromReadWriters`
- Automatic registration with the global registry
- Type-safe `format` method on interaction instances

### Registry Pattern

Formats are registered in a global registry keyed by element ID:
```scala
private val registry = mutable.Map[String, InteractionFormat[?, ?, ?]]
```

This enables:
- Lazy registration (formats registered when companion objects load)
- Type-erased storage (necessary for mixed-type collections)
- Runtime lookup by element ID

### Compatibility

The implementation is fully compatible with existing code:
- Uses existing `Serializer` and `InteractionVariableHistory` types
- Works with `derives ReadWriter` via macro expansion
- No breaking changes to existing APIs
- `WorkbookInteractionElementFull` now has a `def format` method that defaults to `fromReadWriters`

## Migration Guide

### For Existing Interactions

1. Remove `derives upickle.default.ReadWriter` from the case class
2. Add `override def format` method to return the companion's format
3. Make companion object extend `InteractionFormat.Provider`
4. Implement `elementIdPrefix`

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

object TextInteraction extends InteractionFormat.Provider[String, Unit, Unit] {
  override def elementIdPrefix: String = "TextInteraction"
  // ...
}
```

### Testing

Test your format:

```scala
class MyInteractionSpec extends FunSuite {
  test("MyInteraction format is registered") {
    assert(InteractionFormat.forElementId[State, Grade, Scaffold]("MyInteraction").isDefined)
  }
  
  test("Format can serialize/deserialize history") {
    val format = InteractionFormat.forElementId[State, Grade, Scaffold]("MyInteraction").get
    val history = InteractionVariableHistory.empty[State, Grade, Scaffold].addState(testState)
    val serialized = format.historySerializer.serialize(history)
    val deserialized = format.historySerializer.deserialize(serialized)
    assertEquals(deserialized, history)
  }
}
```

## Best Practices

1. **Always override format**: Each interaction should override `format` to use the companion's format
2. **Use elementIdPrefix consistently**: Should match the interaction's element ID used in sync
3. **Prefer Provider trait**: Extending `Provider` handles registration automatically
4. **Handle missing formats gracefully**: Always check for `None` when using `forElementId`
5. **Document custom serializers**: If using custom serializers, explain why

## Examples in Codebase

See these interactions for working examples:

- `TextInteraction`: Simple string-based state (uses Provider)
- `TurtleRecreateShapeInteraction`: Complex programming state with grading (uses Provider)

## Comparison with Original Proposal

| Aspect | Original Proposal | Implementation | Rationale |
|--------|------------------|----------------|-----------|
| Type parameter on interaction | Yes | No (uses `def format`) | Better backward compatibility |
| Registry | Yes | Yes (`forElementId`) | Backend lookup without instances |
| Provider trait | No | Yes | Automatic registration |
| Sealed trait | Yes | No | Open-world design |

### Why `def format` instead of `F <: InteractionFormat`?

Using a method (`def format`) instead of a type parameter (`F <: InteractionFormat`) was chosen because:

1. **Simpler migration** - No need to update all existing type signatures
2. **Backward compatible** - Existing code continues to work
3. **Less verbose** - No type parameter propagation through the codebase
4. **Flexible** - Can still be type-safe with proper implementation

### Why Registry?

The registry (`InteractionFormat.forElementId`) enables:

```scala
// On backend, after reading from database
val elementId = extractElementId(syncContext)
val format = InteractionFormat.forElementId[State, Grade, Scaffold](elementId)
val history = format.historySerializer.deserialize(historyJson)
```

This solves the core problem: **serialization without interaction instances**.

## Future Enhancements

Potential improvements to consider:

1. **Compile-time verification**: Use Scala 3 macros to verify format implementation
2. **Format versioning**: Support schema evolution
3. **Lazy loading**: Load formats on-demand rather than at startup
4. **Format discovery**: Auto-discover formats via Scala 3 given instances
