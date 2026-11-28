# Interaction formats and histories

`InteractionFormat` is a sealed family of singleton values in
[`InteractionFormat.scala`](../modules/core/shared/src/main/scala/it/evadid/workbook/abstractions/InteractionFormat.scala).
A format describes saved state, grading information and scaffolding information.
It has no workbook, exercise ID or element-factory dependency. Several exercises
can share a format.

Consumers carry one parameter, `[F <: InteractionFormat]`. Scala match types
`InteractionFormat.State[F]`, `Grading[F]` and `Scaffolding[F]` recover its value
types. Only format declarations use the `Typed[S, G, A]` implementation base.
The closed registry makes wire names explicit and decodable on every platform;
adding a format requires deploying its codec wherever that history is exchanged.

A `WorkbookInteractionElement[F]` obtains its singleton with `ValueOf[F]` and
exposes it as `interactionFormat`. The format owns state, additional feedback,
event and complete-history codecs. `codecsFor(format)` preserves the generic
format association at call sites; its single internal narrowing bridges Scala's
invariant codecs and singleton-dependent types. Exercise-specific constraints,
such as table dimensions or graph axes, belong in `validateState` and are checked
when applying history. They cannot be inferred by a server without that exercise.
Element definitions have their own codecs; see [session data](session-data-serialization.md).

## Current wire format

Persisted names are fixed literals, never derived from class names, so classes and
packages can be renamed without touching saved data:

| Saved value | Name source |
| --- | --- |
| Format | `Typed(name, …)`, resolved by the finite `knownFormats` registry |
| Event | `@key` on each event case class (`$type`) |
| Grader | its companion's `codec` (`serializedType`); a format lists the grader codecs it accepts |

Unknown names are rejected, without dynamic class loading or element-ID lookup;
[PersistedNamesSpec](../modules/core/shared/src/test/scala/it/evadid/workbook/serialization/PersistedNamesSpec.scala)
pins them.

```scala
import it.evadid.workbook.abstractions.InteractionFormat.TextInteractionFormat
import it.evadid.workbook.history.InteractionVariableHistory
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.history.events.AnswerChangedEvent
import java.time.LocalDateTime

val history = InteractionVariableHistory.empty[TextInteractionFormat.type].addEvent(
  AnswerChangedEvent[TextInteractionFormat.type]("My answer", LocalDateTime.now(), UpdateImportance.MAJOR))
val transport = InteractionHistoryJson.fromHistory(TextInteractionFormat, history)
val decoded = InteractionHistoryJson.deserialize(transport.serialized)
val restored = decoded.typedAs(TextInteractionFormat)
```

`InteractionHistoryJson(format, historyJson)` is the transport/storage boundary.
`toTyped` uses its own format; `typedAs(expected)` additionally checks the expected
format before decoding. `deserialize(json)` validates the inner history immediately;
a plain uPickle `read[InteractionHistoryJson]` reconstructs the envelope, with payload
validation deferred until `toTyped` or `typedAs`. Merging different formats fails
immediately. There are no older-schema readers, fallback formats or workbook-dependent
type resolution; [storage](database/interaction-history.md) defines how malformed records are handled.

## Event contract

`InteractionVariableHistory[F]` stores one `InteractionEvents[F]` set. Its events
are answer changes, grading starts/completions and scaffolding starts/completions.
Every event has a client-generated [`EventId`](../modules/core/shared/src/main/scala/it/evadid/workbook/history/events/EventId.scala)
(a canonical UUID), its associated answer, `LocalDateTime` and importance. Completed
feedback derives its answer and timestamp from the typed result.

Events are immutable: an ID names exactly one content, and a revision is a new event
with a new ID. Merging unions events, so retries are idempotent and distinct same-time
events survive, while a different event under a known ID is rejected
(`InteractionEvents` requires unique IDs). IDs are preserved during decoding; missing
or malformed IDs are rejected rather than generated on read.
Saved error statuses (`TestError`, `ScaffoldingError`) keep only the messages of an error
and its causes; stack traces stay in logs. State/feedback history views are derived projections,
not separate persisted stores. A newer grading start represents a retry in progress;
completion wins a timestamp tie. Graders are matched by their content key: a hash of the persisted grader type and its
parameters, so graders of one type (for example two unit tests) differ by their parameters.

Starts default to `TEMPORARY`, completions to `MAJOR`. `cleanedHistory()` is the one
history cleanup: it drops DEFAULT answers once another answer exists and TEMPORARY answers
once a later one exists, with feedback tied only to them; the current answer always stays.
Transmission strategies are described with [storage](database/interaction-history.md).

## Verification

[`InteractionHistoryJsonSpec`](../modules/core/shared/src/test/scala/it/evadid/workbook/sync/model/InteractionHistoryJsonSpec.scala)
checks JSON/MessagePack envelope round trips for every format, populated answer and
feedback histories, heterogeneous sequences, timestamp precision and malformed inputs.

```sh
sbt 'coreJVM/testOnly *InteractionHistoryJsonSpec' 'coreJS/testOnly *InteractionHistoryJsonSpec'
```
