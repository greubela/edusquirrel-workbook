# Session export and import

`SessionData` contains the save timestamp, user identity, public user configuration,
workbook definition, and `Map[SyncContext, InteractionHistoryJson]` histories.
Session credentials are excluded.

`SessionData.createSessionData` exports each interaction through its format codec.
The self-describing envelope can be decoded independently of workbook registries;
applying it to an interaction also checks the expected format and exercise-specific
state constraints. Unknown formats or malformed exports fail explicitly. Only the
current schema is supported.

Workbook definitions use `SerializedWorkbook(rootId, elements)` and the registered
element factories. See [interaction formats](interaction-format.md) for the history
contract and [state and serialization](state-and-serialization.md) for ownership.
