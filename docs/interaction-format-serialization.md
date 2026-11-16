# InteractionFormat Serialization Design

## Overview

`InteractionFormat` is a trait that provides type-level association between an interaction type and its serializers. Each interaction has exactly one format instance implemented as a case object (the companion object of the interaction class).

## Design Principles

### 1. Format as Case Object
- Format instances are case objects (singletons), naturally serializable
- Only the object's name (fully qualified class name) is serialized
- No serialization issues with non-serializable fields (like `Serializer`)
- Can be safely passed across server boundaries

### 2. Format is Serializable
**Critical Requirement**: `InteractionFormat` MUST be serializable because:
- Format instances are passed from client to server as part of `InteractionHistoryJson`
- Custom workbooks may use interaction formats not known to the server
- The format is needed on the server to deserialize the history JSON

### 3. Format Registration
All allowed format class names are registered in `InteractionFormat.allowedFormats` via static initialization when the interaction companion objects are loaded. This happens automatically when the interaction definitions are imported/used.

The registration happens in the companion object of each interaction, which extends `InteractionFormat`. When the object is loaded, it implicitly registers itself by calling `InteractionFormat.registerFormat`.

**Alternative approach** (if static initialization doesn't work): Formats can be registered explicitly by importing the interaction companion objects, or by calling `InteractionFormat.registerFormat` for each known format.

### 4. Serialization Format
Format is serialized as a string containing the fully qualified class name:
```scala
// Serialized format
"it.evadid.workbook.elements.interactionElements.basic.TextInteraction"
```

### 5. Deserialization Flow
1. Client creates `InteractionHistoryJson(format, json)` with known format
2. Client serializes and sends to server
3. Server receives and deserializes format class name from JSON
4. Server looks up format class name in `allowedFormats` set
5. If found, server loads format via `InteractionFormat.fromClassName(className)`
6. If not found, throws descriptive error

## Usage in Interaction Definitions

```scala
case class TextInteraction(override val elementId: String) 
  extends WorkbookInteractionElement[String] {
  override def format: InteractionFormat[String, Unit, Unit] = TextInteraction
}

object TextInteraction extends InteractionFormat[String, Unit, Unit] {
  override def stateSerializer: Serializer[String] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[...] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[...] = Serializer.fromImplicitRW
}
```

## Database Storage

The format class name is stored alongside the JSON history:
```sql
CREATE TABLE `single_blob_history` (
  `format` VARCHAR(255) NOT NULL,  -- Fully qualified class name
  `history_json` JSON NOT NULL,
  ...
)
```

## Security Considerations

The `allowedFormats` whitelist prevents arbitrary code execution:
- Only registered formats can be deserialized
- Unknown formats throw descriptive errors
- No dynamic class loading from untrusted input

## Registration

Import `InteractionFormats` to register all known formats:
```scala
import it.evadid.core.util.io.serializer.InteractionFormats
```

This is done via static initialization when the object is loaded.
