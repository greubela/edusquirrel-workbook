# InteractionFormat: Type-Safe Serialization for Backend Interactions

## Overview

`InteractionFormat` is a trait that centralizes all serialization logic for a specific interaction type. It enables backend-side serialization/deserialization of interaction histories without requiring runtime access to `InteractionVariable` instances.

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

### 1. Automatic Registration (Recommended)

For interactions with `derives ReadWriter`, simply call `InteractionFormat.register` in the companion object:

```scala
case class MyInteraction(
  override val elementId: String,
  config: String
) extends WorkbookInteractionElementFull[MyState, MyGrade, MyScaffold] derives ReadWriter

object MyInteraction {
  // Automatically register with type-safe serializers
  InteractionFormat.register[MyState, MyGrade, MyScaffold](elementIdPrefix = "MyInteraction")
}
```

### 2. Custom Serializers

For interactions requiring custom serialization logic:

```scala
object CustomInteraction {
  val stateSer: Serializer[CustomState] = new Serializer[CustomState] {
    override def serialize(obj: CustomState): String = /* custom logic */
    override def deserialize(str: String): CustomState = /* custom logic */
  }

  InteractionFormat.registerWithSerializers(
    elementIdPrefix = "CustomInteraction",
    stateSerializer = stateSer,
    historySerializer = Serializer.fromImplicitRW,
    exerciseSerializer = Serializer.fromImplicitRW
  )
}
```

### 3. Retrieving Formats at Runtime

```scala
// Get format by element ID with type parameters
InteractionFormat.get[MyState, MyGrade, MyScaffold]("MyInteraction") match {
  case Some(format) =>
    // Use format.historySerializer.deserialize(serializedHistory)
  case None =>
    // Handle unknown interaction type
}

// Get all registered interaction IDs
val allIds = InteractionFormat.registeredIds

// Use FormatProvider for type-safe lookup
val provider = InteractionFormat.FormatProvider[MyState, MyGrade, MyScaffold]
val format = provider.get("MyInteraction") // throws NoSuchElementException if not found
```

## Integration Points

### Backend Sync Handler

```scala
class CustomSyncHandler(...) extends SyncDestinationHistoryHandler {
  override def storeHistoryJson(syncContext: SyncContext, historyJson: String): Future[SyncSuccess] = {
    // Retrieve the appropriate format based on the element ID
    val elementId = extractElementId(syncContext) // e.g., from syncContext.keyForSerialisation
    InteractionFormat.get(elementId) match {
      case Some(format) =>
        // Use format.historySerializer for type-safe operations
        val history = format.historySerializer.deserialize(historyJson)
        // ... process history
      case None =>
        Future.failed(new IllegalArgumentException(s"Unknown interaction type: $elementId"))
    }
  }
}
```

### Database Storage

```scala
// When storing to database, you can serialize with the appropriate format
val format = InteractionFormat.get(interactionId).getOrElse(
  throw new IllegalStateException(s"No format registered for $interactionId")
)

val serialized = format.historySerializer.serialize(history)
db.insert("INSERT INTO history VALUES (?, ?)", interactionId, serialized)
```

### Database Retrieval

```scala
// When reading from database, deserialize with the appropriate format
val format = InteractionFormat.get(interactionId).getOrElse(
  throw new IllegalStateException(s"No format registered for $interactionId")
)

val history = format.historySerializer.deserialize(storedJson)
```

## Implementation Details

### Type Safety

The implementation uses Scala's type system to ensure:
- State, history, and exercise serializers are always consistent
- Type parameters are propagated correctly through the chain
- No runtime type casting is needed in most cases

### Registry Pattern

Formats are registered in a global registry keyed by element ID:
```scala
private val registry = mutable.Map[String, (String, String, String, Serializer[_])]
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

## Migration Guide

### For Existing Interactions

1. Add the import:
   ```scala
   import it.evadid.core.util.io.serializer.InteractionFormat
   ```

2. Register the format in the companion object:
   ```scala
   InteractionFormat.register[T, GA, SA](elementIdPrefix = "ElementId")
   ```

3. Replace direct `InteractionVariable` usage with `InteractionFormat.get(elementId)` where appropriate

### Testing

Test your format registration:

```scala
class InteractionFormatSpec extends FunSuite {
  test("TextInteraction format is registered") {
    assert(InteractionFormat.get("TextInteraction").isDefined)
  }
  
  test("TurtleRecreateShapeInteraction format is registered") {
    assert(InteractionFormat.get("TurtleRecreateShapeInteraction").isDefined)
  }
}
```

## Best Practices

1. **Register early**: Place registration in companion object initialization
2. **Use elementId consistently**: The prefix should match the interaction's element ID
3. **Prefer automatic derivation**: Use `InteractionFormat.register` over custom serializers unless needed
4. **Handle missing formats gracefully**: Always check for `None` when retrieving formats
5. **Document custom serializers**: Explain why custom logic is needed

## Examples in Codebase

See these interactions for working examples:

- `TextInteraction`: Simple string-based state
- `TurtleRecreateShapeInteraction`: Complex programming state with grading
- `ReorderInteraction`: Generic reorder interactions with different element types

## Future Enhancements

Potential improvements to consider:

1. **Compile-time verification**: Use Scala 3 macros to verify format registration
2. **Format versioning**: Support schema evolution
3. **Lazy loading**: Load formats on-demand rather than at startup
4. **Format discovery**: Auto-discover formats via Scala 3 given instances
