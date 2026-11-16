# SessionData Serialization Design

## Overview

`SessionData` stores interaction history using `InteractionHistoryJson` to keep the format and JSON together, enabling type-safe deserialization.

## Design

```scala
case class SessionData(
  timestampStorage: LocalDateTime,
  knownUsageData: Map[SyncContext, InteractionHistoryJson[?, ?, ?]],
  userInfo: User,
  userConfig: Option[UserConfig],
  workbookInfo: AllWorkbookInfo
) derives ReadWriter
```

## Why InteractionHistoryJson?

### Problem with Raw Strings

Previously, history was stored as `Map[SyncContext, String]` where the string was raw JSON:

```scala
// OLD - raw JSON string, format lost
val historyMap: Map[SyncContext, String] = Map(
  syncContext -> jsonStr
)
```

**Problems**:
- Format information is lost when deserializing
- Need registry lookup to find the right format
- No type safety - can deserialize with wrong format

### Solution with InteractionHistoryJson

```scala
// NEW - format stored with JSON
val historyMap: Map[SyncContext, InteractionHistoryJson[?, ?, ?]] = Map(
  syncContext -> InteractionHistoryJson(format, jsonStr)
)
```

**Benefits**:
- Format is stored alongside history for deserialization
- Type-safe - format matches the JSON structure
- Can deserialize without registry lookup
- Format is a case object (naturally serializable)

## Usage Pattern

### Creating SessionData

```scala
val historyJson = InteractionHistoryJson.fromHistory(
  interaction.interactionVariable.format,
  interaction.interactionVariable.currentHistory
)

val sessionData = SessionData(
  timestampStorage = LocalDateTime.now(),
  knownUsageData = Map(syncContext -> historyJson),
  userInfo = user,
  userConfig = config,
  workbookInfo = workbook
)
```

### Loading SessionData

```scala
// Read session data from storage
val sessionData: SessionData = read[SessionData](json)

// For each interaction, use the stored format
sessionData.knownUsageData.foreach { case (syncContext, historyJson) =>
  val history = historyJson.toHistory  // Uses stored format
  interaction.interactionVariable.updateHistory(history)
}
```

## Database Storage

For database persistence, use `InteractionHistoryJson.stored`:

```scala
// Store format with history
val storedJson = InteractionHistoryJson.stored(key, historyJson)

// Retrieve from database and parse
val retrievedJson = ujson.read(dbJson)
val historyJson = InteractionHistoryJson(format, ujson.write(retrievedJson.obj.getOrElse("fullHistory", retrievedJson)))
```

## Summary

`InteractionHistoryJson` provides:
- **Format + JSON together** - No need for registry lookup
- **Type-safe** - Format matches the JSON structure
- **Serializable** - Format is a case object (naturally serializable)
- **Flexible** - Can be stored in database or passed across server boundaries

This approach ensures that format information is always available when deserializing history, without needing a registry or complex type inference.
