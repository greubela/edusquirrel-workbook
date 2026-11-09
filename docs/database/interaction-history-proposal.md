# Proposal: relational interaction history

Status: design proposal; no change to the current persistence format.

Use separate tables for answer events, grading results and scaffolding in the
same MySQL database. These records have different lifetimes and queries, but
belong to the same interaction. Keeping them in one database preserves foreign
keys and atomic writes; separate physical databases would add coordination work
without helping this model.

## Tables

| Table | Key and relationships | Stored data |
| --- | --- | --- |
| `interaction` | Server `BIGINT` primary key; unique `(user_id, program_id, scenario_id, interaction_key, exercise_revision)` | Owner and workbook context; current server change revision |
| `answer_state` | Server `BIGINT` primary key; FK to interaction; unique `(interaction_id, state_hash)` | One typed answer as JSON; SHA-256 of its canonical representation |
| `answer_event` | Client-generated UUID primary key; composite FK `(interaction_id, state_id)` to answer state | Edit importance, client occurrence time and server receipt time |
| `grading_result` | Client-generated UUID primary key; composite FK `(interaction_id, state_id)` to answer state | Stable grader key and configuration revision, completion time, status, feedback JSON and additional result JSON |
| `scaffolding_result` | Client-generated UUID primary key; composite FK `(interaction_id, state_id)` to answer state | Stable scaffolding key and configuration revision, completion time, feedback JSON and additional result JSON |
| `interaction_change` | Primary key `(interaction_id, revision)`; FK to interaction | Record kind, record UUID and operation, for incremental synchronization |

Use a unique `(interaction_id, id)` key on `answer_state` to support the composite
foreign keys. This prevents a result from referencing another interaction's
answer. Ownership always comes from the authenticated account, not a submitted
`user_id`. Scope every read and write through that ownership relationship.

Use `utf8mb4` for text, binary comparison for identifiers, `JSON` for typed
payloads, `BINARY(16)` for UUIDs, `BINARY(32)` for hashes and `DATETIME(6)` in UTC
for timestamps. Retain the existing importance/status semantics as explicit
columns with constraints. Do not put serialized history maps into JSON columns;
JSON holds individual domain values that vary between exercise types.

## Why states and events are separate

The current model attaches grading and scaffolding to answer **values**, whereas
events record edits. A learner can enter A, then B, then A again. Those are three
events but two answer states; grading for A belongs to A regardless of when it
finishes. An unedited default answer can also have grading without inventing an
edit event. This distinction matches `InteractionVariableHistory` and its
state-keyed grading/scaffolding maps.

Hash the exercise's canonical typed answer encoding, not arbitrary JSON text or
a Scala object's `hashCode`. Include the exercise revision in the interaction
identity so changed exercise semantics cannot reuse old grading. Verify payload
equality when reusing a hash; reject a conflicting payload rather than silently
merging it. Retain only the current supported payload schema, with validation at
the storage boundary; this design does not require legacy readers.

## Write and synchronization rules

- Allocate an event/result UUID when the logical operation is created and keep
  it across retries. Duplicate IDs with identical content are idempotent;
  conflicting content for an existing ID is rejected. Do not deduplicate by time.
- Store individual immutable events/results. A new grade never replaces the
  answer state or edit event. Preserve distinct grading runs; derive the current
  result per `(state_id, grader_key, configuration_revision)` using the domain's
  existing merge rules rather than arrival order alone.
- In one transaction, lock the owning interaction row, insert the records,
  increment its revision, and append change-journal entries. Assign revisions
  only to accepted changes. The row lock makes revisions safe for concurrent
  incremental reads; an auto-increment ID alone is not a commit-order guarantee.
- Synchronize using server revisions per interaction. Client timestamps describe
  when work happened; they are not reliable change cursors. Fetch current records
  and cursor in a consistent transaction/snapshot. Journal deletion tombstones
  if individual records can be deleted; expire journal entries only with a
  defined full-resync rule for clients with older cursors.
- Keep filtering by `SyncStrategy` before uploading. Persist feedback independently
  even if the answer has not changed. Reject invalid incoming records explicitly;
  isolate malformed stored records without dropping other valid records.

For example, if grading for A finishes after the learner has entered B, insert
the result referencing A's `state_id`. Reading the interaction still selects B
as the current answer and uses the result only when looking up feedback for A.

## Queries and indexes

Load a workbook's interaction IDs using the context/owner index, then bulk-fetch
events and results for those IDs; avoid a query per interaction. Index events by
`(interaction_id, occurred_at, event_id)`, grading by
`(interaction_id, state_id, grader_key, configuration_revision, completed_at)`,
and scaffolding similarly. The change journal's primary key supports incremental
reads. Add a current-result projection only if measurements show reconstruction
is expensive; the append-only rows remain authoritative.

Client times can tie or disagree across devices. Define a deterministic edit
ordering/tie-break in the domain model before implementation; server receipt
order alone can incorrectly make an old offline edit the newest answer.

Account deletion should remove owned interactions and their child records using
an explicit retention policy. A workbook reset deletes the scoped interaction
data transactionally; it must also invalidate the relevant client sync cursors.

Implement this as a new current storage contract once the design is agreed.
During development, discard unsupported old history records rather than adding
dual writes or migration readers for `events`/`eventsWithKey` JSON blobs.
