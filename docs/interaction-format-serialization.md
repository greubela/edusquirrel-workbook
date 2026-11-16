# InteractionFormat Serialization Design

## Overview

`InteractionFormat` is a trait that provides type-level association between an interaction type and its serializers. Each interaction has exactly one format instance implemented as a case object (the companion object of the interaction class).

## Design Principles

### 1. Format as Case Object
- Format instances are case objects (singletons), naturally serializable
- Only the object's name (fully qualified class name) is serialized
- No serialization issues with non-serializable fields (like `Serializer`)
- Can be safely passed across server boundaries

### 2. Format is Serializable (Critical Requirement)
`InteractionFormat` MUST be serializable because:
- Format instances are passed from client to server as part of `InteractionHistoryJson`
- Custom workbooks may use interaction formats not known to the server
- The format is needed on the server to deserialize the history JSON

### 3. Compile-Time Known Formats
All interaction formats are known at compile time:
- The client has all interaction definitions loaded
- Format is passed explicitly from client to server
- If an unknown format is received, a descriptive error is thrown

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
4. Server loads format via `InteractionFormat.fromClassName(className)`
5. If format class isn't available, throws `formatNotKnown` error

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

If an unknown format class name is received:
- Error message: "Format class '...' is not known. This may indicate an unknown interaction type."
- No arbitrary code execution risk
- Clear indication of missing interaction definitions

## Summary

- Format is a case object (singleton) - naturally serializable
- Serialization stores only the class name string
- All formats are compile-time known (client-side)
- Unknown formats throw descriptive error on deserialization
- Simple and secure approach without complex registration mechanisms
