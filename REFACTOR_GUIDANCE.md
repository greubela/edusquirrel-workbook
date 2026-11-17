# Refactor Guidance - variable-refactor-night-action

## Original Prompt

Fetch the branch feature/event-history-sync and try to continue making everything compile. It is in the middle of a HUGE refactor with the main points being:
- Introducing InteractionFormat as central Type Parameter for things like WorkbookInteraction etc.
- Introducing InteractionHistoryJson as semi-typed variant (esp. with the format as value to enable de-serialisation)
- InteractionHistory as Event-Based Data-Structure 
- Strong typing wherever possible (esp. also on server that previously didn't know the exact format of InteractionVariableHistory)

## Requirements

1. **Make everything compile** - sbt might have issues with the special character in the user name; use the batch script `compile.bat` which should work, even though `deployAll` should be used.

2. **Re-write tests** to use the new structures (remove tests that have assumptions that are no longer relevant).

3. **DO NOT SUPPORT LEGACY CODE OR DATA STRUCTURES**

4. **NO SENSELESS ADAPTERS**

5. **Commit and push regularly** on branch "variable-refactor-night-action"

6. **Continue until finished** - do not randomly stop. The user has a lot of free time but isn't monitoring regularly.

## Current State

- Branch: `variable-refactor-night-action`
- The refactor introduced `InteractionFormat` as central type with type members
- `InteractionVariableHistory[F <: InteractionFormat]` is event-based
- `InteractionHistoryJson[F <: InteractionFormat]` wraps format and JSON

## Key Guidance for Fixing

### Focus on Easily Fixable Issues

1. **Use ValueOf for type-level operations**:
   - "you can get the type and the value of a singleton since ValueOf is standard scala syntax and the value is implicitely there"
   - When you need a specific format type, add `using v: ValueOf[F]` to the method signature
   - The value is available implicitly via `v.value`

2. **Don't make major structural changes** unless explicitly asked:
   - Keep the `InteractionFormat` trait with type members (`InteractionStateType`, `GradingInfoType`, `ScaffoldingInfoType`)
   - Keep `type F <: InteractionFormat` in each format object
   - Don't change the overall architecture unless necessary

3. **As highly typed as possible**:
   - Use `InteractionHistoryJson[F <: InteractionFormat]` with type parameter
   - Keep format as a value (`format: F`) for serialization/deserialization
   - Avoid existential types like `InteractionFormat` as a type parameter when possible

4. **InteractionHistoryJson should only support toTyped**:
   - Merging, timestamps etc. are available only on the typed access
   - No `lastTimestamp` method on `InteractionHistoryJson` itself

5. **If stuck on typing, focus on another area first**:
   - Don't spend too long on one type issue
   - Work on other files and come back to typing issues
   - Commit what you have, even if it's WIP

## Common Patterns

### Type Parameter Pattern
```scala
def serialize[F <: InteractionFormat](interaction: InteractionVariable[F])(using v: ValueOf[F]): String = {
  val history = interaction.currentHistory.cleanWithStrategy(syncInformation.syncStrategy)
  v.value.historySerializer.serialize(history.asInstanceOf[InteractionVariableHistory[v.value.F]])
}
```

### Format Object Pattern
```scala
object TextInteractionFormat extends InteractionFormat {
  type InteractionStateType = String
  type GradingInfoType = Unit
  type ScaffoldingInfoType = Unit
  type F = TextInteractionFormat  // NOT TextInteractionFormat.type
}
```

## Known Issues (as of this session)

1. `InteractionHistoryJson` - type mismatch: `format.F` vs type parameter `F`
2. `SyncInformationWithContext.serialize` - needs `ValueOf[F]`
3. `BufferedSyncDestinationHandler` - needs `ValueOf[F]` for `CachedReadRequestSingle`
4. `HistoryJson` - needs `InteractionEvents` import, and `ujson.Value` type issues
5. `WorkbookElementFactory` - missing `TextInteraction2` and `ProgrammingExercise` types

## Commit Strategy

- Commit regularly on `variable-refactor-night-action`
- Use descriptive commit messages like "WIP: Fix InteractionFormat type parameters"
- Push after each meaningful commit
