# InteractionFormat Serialization Design

## Overview

`InteractionFormat` is a **sealed trait** with case objects as implementations, providing type-level association between an interaction type and its serializers.

## Design

### Why Sealed Trait?

- **All implementations known at compile time** (must be in same file as trait)
- **Compiler ensures all cases are handled**
- **`derives ReadWriter` works automatically** for sealed traits with case objects

### Why Case Objects?

- **Naturally serializable** (they're singletons)
- **Only class name is serialized**
- **No serialization issues** with non-serializable fields (like Serializer)
- **Can be safely passed** across server boundaries

### Format Storage

All known format implementations are stored in `InteractionFormat.knownFormats`:

```scala
val knownFormats: Set[InteractionFormat[?, ?, ?]] = Set(
  TextInteraction, ChoiceInteraction, // ... all known formats
)
```

This provides:
- Type-safe access to all formats
- Compile-time verification of known formats
- Clear error messages for unknown formats

## Usage

```scala
// Interaction class - references companion as format
case class TextInteraction(elementId: String) 
  extends WorkbookInteractionElementFull[String, Unit, Unit] {
  override def format: InteractionFormat[String, Unit, Unit] = TextInteraction
}

// Companion object - IS the format instance (case object)
object TextInteraction extends InteractionFormat[String, Unit, Unit] {
  override def stateSerializer: Serializer[String] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[...] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[...] = Serializer.fromImplicitRW
}
```

## Strong Typing

- **Type parameters**: `[T, GA, SA]` for state, grading, scaffolding
- **Compile-time safety**: Type errors caught at compile time
- **No runtime casting**: Deserialization produces correctly typed format

## Serialization

- Format serialized as **class name string**
- Deserialization loads singleton case object
- All formats must be compile-time known (in `knownFormats`)
- Unknown formats throw descriptive error

## Database Storage

```sql
CREATE TABLE `single_blob_history` (
  `format` VARCHAR(255) NOT NULL,  -- Fully qualified class name
  `history_json` JSON NOT NULL,
  ...
)
```

## Summary

- **Sealed trait**: All implementations known at compile time
- **Case objects**: Naturally serializable singletons
- **Strong typing**: `[T, GA, SA]` type parameters ensure compile-time safety
- **Auto-serialization**: `derives ReadWriter` works automatically
- **Type-safe operations**: No unsafe casting; all operations use typed format

## See Also

- [`interaction-format.md`](interaction-format.md) - Overview of the InteractionFormat API
