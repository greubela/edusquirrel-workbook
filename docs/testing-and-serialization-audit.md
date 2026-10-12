# Serialization inventory and verification

The maintained inventory is
[tools/dev/audit-scala-serialization.py](../tools/dev/audit-scala-serialization.py).
It scans production case classes, sealed traits and enums, classifying default
derived codecs, explicit codecs and documented exceptions. Counts change with the
source; run the checker for the current totals instead of citing an old test run.

```sh
python3 tools/dev/audit-scala-serialization.py --check
python3 tools/dev/audit-scala-serialization.test.py
sbt 'coreJVM/test' 'coreJS/test' 'client/test' 'server/test' 'worker/test'
```

Missing codecs and stale entries in
[serialization-exceptions.json](serialization-exceptions.json) fail the inventory
check. The scanner is a source inventory, not proof that a codec preserves runtime
behavior or that all branches are tested. Round-trip and behavioral tests supply
that evidence. See [state and serialization](state-and-serialization.md) for
current authoring patterns.

## What needs a codec

Persisted answers, exercise definitions, command requests/results and transport
snapshots must have a stable cross-platform uPickle contract. Prefer default
`derives ReadWriter` for data models; retain an explicit companion codec where
constructor evidence, an open hierarchy, validation or wire compatibility requires
it. Library-type codecs belong in `DefaultSerializer`.

Live callbacks, futures, reactive subscriptions, DOM/worker handles, services,
connections and controllers are not automatically meaningful snapshots. Persist
their explicit data instead. Each intentional exception has a reason in the
manifest; a runtime-bearing case class is not permission to move a saved answer
type into the client module.

## Important explicit contracts

| Model/boundary | Contract that tests must preserve |
| --- | --- |
| Numeric geometry and SVG builders | Reconstruct numeric evidence; mutable builders resume from current geometry, not the constructor seed |
| Workbook elements | Factories keep their definition formats; self-contained root codecs include the reference registry and prerequisites |
| Programming/AST types | Preserve the active representation and literal/type descriptors; reject unsupported tags instead of reconstructing executable serializer callbacks |
| Interaction history | Typed events, grading and scaffolding maps are history JSON; use `InteractionVariable.serializeHistory`/`deserializeHistory` |
| Execution transport | Typed `ExecutionInfo[T]` success/failure and `ExecutionClientResponse`; requests/results must decode on the selected backend |
| Snap primitive defaults | Supported tagged primitives retain integer/double distinctions and special floating values; arbitrary executable objects are rejected |
| Evacuation graphs/simulations | Preserve graph weights and routing/event state; decoded simulations can continue without serializing a weight function |
| Unicode evidence | Preserve UTF-16 code units, including deliberately malformed input; lossy UTF-8 replacement would discard evidence |

Representative suites are `SystematicDerivedCodecSpec`, `DefaultValueCodecsSpec`,
`AdditionalDefaultCodecsSpec`, `RenderingValueCodecSpec`, `PracticeValueCodecSpec`,
`InteractionHistoryPackageSpec`, `CommandPayloadCodecsSpec`,
`SystematicClientCodecSpec` and `ConfigurationCodecSpec`. Use
[the shared-core test guide](core-package-unit-tests.md) to locate behavioral suites.

## Maintaining tests

Keep a test when it checks a supported contract with independently specified
expectations. Repair fixtures/imports after package moves and compare serialized
exception data rather than throwable identity. Replace manual demos with discoverable
assertions when they cover useful behavior. Remove tests of deleted APIs or
meaningless smoke assertions instead of hiding failures with `ignore`.

Test both JSON and MessagePack, concrete and interface codecs, legacy/default
fields, malformed input and behavior after restoration. Command tests use synthetic
data and local/injected handlers rather than sending real mail or opening a
production connection. Browser tests cover integration paths that Node/JVM tests
cannot prove. Passing an inventory check or a named suite does not establish
100% project coverage.
