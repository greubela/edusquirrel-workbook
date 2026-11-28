# Serialization boundaries and verification

## What needs a codec

A type needs a uPickle codec only when its values cross a serialization boundary:

| Boundary | Types |
| --- | --- |
| Database and browser storage | Interaction histories through their `InteractionFormat` (state, grading, scaffolding and events); user, session and synchronization data |
| Workbook definitions | Workbook and structure elements, interaction definitions and their grading/scaffolding configuration. Workbooks are to be loaded from the database, so these keep codecs even before that loader exists |
| Server and worker transport | `ExecutionCommandFactory` requests and results, `ExecutionClientResponse` and serialized exceptions |

Everything else has no codec: parser tokens, syntax trees and symbol tables, VM
runtime and analysis results, layout, flowchart and SVG geometry, editor and UI
state. Do not derive a codec for completeness, for a hypothetical use or to
compare values in a test; compare the values themselves. Each derivation is
compiled for the JVM and for Scala.js, and derivations dominated `core`'s
compile time before this rule (about three quarters of it).

The compiler checks that codecs suffice. `write`/`read`, `InteractionFormat` and
`ExecutionCommandFactory` require a codec for the type and, through derivation,
for every field type. When a type starts crossing a boundary, it fails to compile
until it and its fields have codecs; add them then, together with a round-trip
test. Give a sealed hierarchy codecs for all of its members or for none.

Never add `ReadWriter[Any]` or serialize arbitrary values with `toString`. A genuinely
dynamic payload may use `ujson.Value`; formats without additional grading/scaffolding
data use `Unit`. Prefer `derives ReadWriter` for boundary models. Keep an explicit companion
codec where constructor evidence, an open hierarchy, validation or wire
compatibility requires it. Library-type codecs belong in `DefaultSerializer`.
Live callbacks, futures, reactive subscriptions, DOM/worker handles, services,
connections and controllers are not snapshots: a boundary type stores their
explicit data instead.

## Important explicit contracts

| Model/boundary | Contract that tests must preserve |
| --- | --- |
| Numeric geometry and SVG builders | Reconstruct numeric evidence; mutable builders resume from current geometry, not the constructor seed |
| Programming expressions and data types | Preserve the active representation and literal/type descriptors; reject unsupported tags instead of reconstructing executable serializer callbacks |
| Workbook elements | `ReadWriter[WorkbookElement]` writes a flat `SerializedWorkbook` listing each element once; single-element text formats embed referenced elements |
| Interaction history | `InteractionFormat` owns typed event/history codecs; transport uses `InteractionHistoryJson` |
| Execution transport | `ExecutionClientResponse` carries a typed result or serialized failure, from which the client builds `ExecutionInfo[T]`; requests/results must decode on the selected backend |
| Snap primitive defaults | Supported tagged primitives retain integer/double distinctions and special floating values; arbitrary executable objects are rejected |
| Evacuation graphs/simulations | Preserve graph weights and routing/event state; decoded simulations can continue without serializing a weight function |
| Unicode evidence | Preserve UTF-16 code units, including deliberately malformed input; lossy UTF-8 replacement would discard evidence |

See [the shared-core test guide](core-package-unit-tests.md) for test locations
and commands.

## Maintaining tests

Keep a test when it checks a supported contract with independently specified
expectations. Repair fixtures/imports after package moves and compare serialized
exception data rather than throwable identity. Replace manual demos with discoverable
assertions when they cover useful behavior. Remove tests of deleted APIs or
meaningless smoke assertions instead of hiding failures with `ignore`.

Test both JSON and MessagePack, concrete and interface codecs, current optional/default
fields, malformed input and behavior after restoration. Command tests use synthetic
data and local/injected handlers rather than sending real mail or opening a
production connection. Browser tests cover integration paths that Node/JVM tests
cannot prove. Passing a named suite does not establish 100% project coverage.
