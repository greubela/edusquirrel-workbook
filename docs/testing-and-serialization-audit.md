# Testing and default serialization audit

This audit started from main `87a2c402` and was integrated with `6b468333`, including
the programming package refactor, instance-based state fingerprints, and the
turtle geometry/grading changes. Existing turtle tests use the relocated geometry
`Line` type. Snap codec
models now live under `programming.state.snap`. It extends the earlier
[core package audit](core-package-unit-tests.md), concentrating on legacy packages
whose examples were manual programs rather than discoverable test suites. Package
names alone are not coverage measurements: many existing tests live in a parent
package and already exercise their children.

## Regression coverage and fixes

| Area | Coverage and resulting corrections |
| --- | --- |
| Bit sequences and binary utilities | Every byte, every Long bit including the sign bit, signed extremes, head/tail boundaries, left padding and truncation. Fixes Int shifts used for Long values, sign-bit detection, reversed head slicing, discarded padding, empty tails and an incorrectly forced eight-bit minimum in explicit-width padding. |
| Binary compression/converters | Empty input, signed bytes, deterministic random inputs, runs crossing 255, all registered minimum-encoder pipelines and 32-bit length headers. Fixes empty bit-plane reconstruction, length-header overflow and decoding subsequent elements from the wrong packet. |
| Bit codecs | Empty/singleton Huffman alphabets, Unicode and all byte symbols, large frequencies, prefix-free codes, incomplete payloads and repetition runs. Fixes singleton/empty Huffman handling, frequency overflow, discarded padded data and silently accepted incomplete codes. Rejects invalid sizes and runs. |
| Permutations | Reproducible seeded shuffles, inverses, plane ordering, composition and constructor validation. Fixes one-based shuffle indices and composition order. |
| Byte codecs | Signed/index/fixed primitive round trips and malformed inputs. Boolean wire values remain `0 = true`, `1 = false`; other values and incorrect lengths now fail. |
| Padded byte collections | Left padding, empty collections, zero-width elements, widths 127/128/255 and malformed headers. Fixes signed interpretation of the width byte and empty-input failure; rejects widths exceeding the one-byte format. |
| Routing | Independently specified weighted routes, cycles, zero-cost edges, unreachable nodes, BFS/A*, cached/reversed routes and a 10,001-node predecessor chain. Reconstruction rejects missing predecessors and cycles instead of returning a partial path or looping. |
| Mutable collections and object pools | Duplicate/list vs set semantics, independent copies, transforms, structural equality, self-addition, absent lookups, factory priority/cache reuse and immutable map snapshots. Implements missing equality/update/remove methods, prevents lookups/removals from creating buckets, avoids self-addition looping and returns None for factory misses. `apply` still creates a mutable bucket. |
| Geometry | Extreme coordinate distances/interpolation, rounding, nearest-point limits, duplicates, empty lists and custom extractors. Uses floating-point subtraction before distance/interpolation and sorts/takes nearest entries without removing equal duplicates or exhausting the input. Negative counts fail. |
| Utilities | Negative cyclic indices including Long boundaries, empty lookup, counters, factory errors and duration formatting. Empty cyclic lookup fails explicitly; hours retain zero minutes and full millisecond output retains leading zeros. |

The audit adds 72 tests. The first bit/compression regression run failed 19 of 25 tests. These are actual
behavioral regressions with fixes, rather than tests that merely assert implementation
details. Random fixtures use fixed seeds. Shared suites run on both JVM and Scala.js.

## Default uPickle ReadWriters

New codecs are available from companion implicit scope, without a serializer-specific
import, for these data models:

- `AppFont`, `BitSequence`, `Position`, `BytePlaneConverter`, `SizeContentIO`, `HuffmanIO[T]`.
- `SearchNode[N,I]`, `RoutingOption[N]` and Dijkstra/BFS/A* information records.
- `BeDataTypeAssigningPossible` and all three concrete outcomes; `BeInfo`, `InfoType`
  and its syntax/runtime/warning enums.
- `UnicodeCharacter`, `LedgerTransfer`, `LedgerError`, `SquareMiddleHashResult`,
  `MiningEnergyEstimate`, `QrCodeRegion` and package-private `QrCodeSymbol`.
- Snap canvas script/layout, reporter kinds/reporters, parsed XML elements, custom
  slots/definitions/obsolete calls/custom-block plans, palette tabs and input kinds.
- `DataEntryToSync[K,D]`, both concrete data-entry records, `SyncStatus[K,V]`,
  `CacheCollectionReport[K,D,CK]`, `InteractionVariableFetchResponse` and
  `SyncFetchedHistory[T]`.

Generic codecs require codecs for their type arguments. A cache report specifically
requires a concrete serializable cache-key identity; the callback-based `CacheKey`
interface does not gain a codec. Timestamp records use the existing LocalDateTime
codec and retain nanoseconds. The two data-entry classes now implement their existing
sealed data-entry interface, making its polymorphic codec usable.

JSON and binary round trips cover every newly supported model, concrete/sealed variants,
all enum values, defaults, numeric precision and behavior after restoration. The
diagnostic hierarchy uses explicit category/name codecs because deriving the nested
enum hierarchy is ambiguous in the current uPickle version. Unicode glyphs are stored
as UTF-16 code units: ordinary UTF-8 binary string encoding replaces unpaired
surrogates, which would destroy the malformed-input evidence this teaching model
explicitly represents. Invalid code units are rejected.

Existing workbook element graph/factory serializers remain the supported format;
these elements are already serializable. Live cache/controllers, callbacks, Futures,
observers, browser DOM/editor sessions and parser types carrying interpreter objects
are not suitable for automatic data-only derivation. Snap primitive descriptors with
`List[Any]` also need a typed schema before deriving a reliable default codec. Method-
local wrappers and path-dependent timer points are runtime implementation details.
The unused `MultiHashMapUnfinished`, `NayukiAdapter` and `BZip2Converter` prototypes
remain outside the supported/tested API; this audit does not claim they are complete.

## Ignored and undiscovered tests

No Scala ignore markers, ignored tags or test filters were present; the baseline
reported zero ignored tests in every module. Three browser tests were conditionally
skipped using `CATALOG_BROWSER_TEST`, `MONKS_BROWSER_TEST` and
`EMBROIDERY_BROWSER_TEST`. They remain useful and now live in dedicated
`*.browser.test.mjs` files with no skip flags. They cover actual rendering, navigation,
saved answers, Koch geometry, cache restoration/refresh and corrupt/unavailable
IndexedDB fallback.

Useful manual `main` examples for bits, bit planes, repetition, run-length coding,
Huffman and routing were replaced by the automated suites above. Removed redundant
`TestParser` prints are covered by the existing parser suites. `TestZipping` contained
only commented code referring to a nonexistent converter; `TestNayukiDeflateConverter`
exercised an unused unimplemented prototype. Both obsolete examples were removed.
Fixtures and platform/helper objects were retained.

The main programming-state refactor left several existing test imports and companion
factory/fingerprint calls stale. These tests now import the relocated state variants,
use the new XML factory helper and call `state.fingerprint()`. Additional assertions
check source/representation distinctions and fingerprint stability after default JSON
and binary serialization. The refactor's production API is retained.

Running every older browser test also exposed structural equality in retained
fullscreen content: a fresh editor with the same case-class parameters was treated
as the previous editor, retaining invalid neuron drafts and the previous mail folder.
Fullscreen retention now compares editor identity. Reopening the same editor instance
still retains its DOM; a newly constructed editor replaces it. A signal-level client
regression and the actual save/reopen browser flows cover both behaviors. Chapter and
editor replacement assertions wait for the requested view before inspecting it,
instead of racing the previous DOM. The compression test uses current
`artifacts/newest/client.js` instead of a stale Scala-version-specific output path.

## Repeatable checks

```sh
sbt -batch coreJVM/test coreJS/test client/test server/test worker/test buildClientDev buildWorkerDev
npm ci
npm run test:static
npx playwright install chromium
npm run test:workbooks
```

The static runner executes all asset/DOM tests. The browser runner discovers all
Playwright test files, including older interaction tests, and requires current client
artifacts. It bundles the actual production CodeMirror loader with exact matching
local package versions; Chromium needs no CDN access. Set `CHROMIUM_PATH` for a system
browser. `tmp/work/browser-tests/` contains only disposable generated assets.

`.github/workflows/tests.yml` runs all five Scala targets, both browser artifacts,
static checks and browser checks on pull requests, main pushes and manual dispatch.
The separate Pages deployment workflow is unchanged. Passing suites establish the
behaviors listed here; this is not a claim of complete line/branch coverage.

Verified on 2026-10-10; all five Scala suites, both browser builds and static
checks were rerun after integrating main `6b468333`:

| Target | Passed | Failed / ignored |
| --- | ---: | --- |
| Shared JVM | 924 | 0 / 0 |
| Shared Scala.js | 912 | 0 / 0 |
| Client Scala.js | 508 | 0 / 0 |
| Server JVM | 5 | 0 / 0 |
| Worker Scala.js | 3 | 0 / 0 |
| Static asset/DOM checks | 23 | 0 / 0 |
| Browser workbook/editor/cache checks | 15 | 0 / 0 |

The complete browser suite passed after the fullscreen fix. After integrating
main `6b468333`, the monk and embroidery browser checks were rerun against freshly
built artifacts to verify the affected turtle interactions.

## Shared-core follow-up: observable state and language resolution

The next pass starts from main `6b20b488`. A search for classes without direct test
references identified the observable implementations and `LanguageMapIdResolver`.
The SVG mutable builder also lacked references to its concrete class name, but
already has behavioral tests through its factory; empty marker traits and unfinished
prototypes were not counted as useful test targets.

Four shared suites add 46 tests, executed on JVM and Scala.js:

- `ObservableValueSpec` (30): initial replay, duplicate suppression, priority and
  listener isolation, subscription cancellation, pending futures, failures and
  recovery, reentrant one-time listeners, constants, tuples and ordered lists,
  synchronous/optional/Future derivations, execution contexts and both queue policies.
- `ExecutionMethodSpec` (5): synchronous/asynchronous results and failures, including
  a callback that throws without being called a second time.
- `StateSpec` (5): distinct updates, bidirectional mappings and their composition,
  failed reverse mappings, and asynchronous update success/failure.
- `LanguageMapIdResolverSpec` (6): language changes, English fallback, failed single
  lookups, partially successful and empty batches, and a delayed map response.

The first observable run exposed 14 failures. Fixes publish combined-list updates
(and an immediately available empty list), validate indices, return a failed Future
from empty observables, detach one-time listeners before callbacks, and evaluate
optional/Future initial inputs once. Derivations now preserve failures, honor the
execution context, and process queued inputs according to the chosen policy. Future
derivations wait for the current Future before starting queued work, preventing an
older completion from overwriting a newer result. Synchronous execution catches
function failures while leaving callback failures outside that catch.

The queue-policy tests use an explicitly drained execution context and promises;
they require no sleeps or timing assumptions. JVM and Scala.js run the same shared
regression tests.

Verification of the follow-up against fetched main `6b20b488`: shared JVM 987,
shared Scala.js 975, client 511, server 5 and worker 3 tests passed, with no failures
or ignored tests. Both development browser artifacts build successfully.
The rebuilt catalogue browser check also passes, including public startup,
IndexedDB reuse and translation refresh.

## Evacuation follow-up: graphics and EVA1 routing

Added default uPickle codecs for 18 immutable value models: `Router`,
`ConnectionInfo`, `EvaPerson`, `CapacityInformation`, `EvaColor`, `HSBColor`,
`EvaFont`, `EvaFileInformation`, `FrameData`, `FloorSpriteProperties`,
`SpriteMapResourceIdentifier`, and the seven concrete basic/animated sprite types.
JSON and MessagePack tests cover nested people/destinations, directional frame maps,
file bytes, Unicode metadata and optional constructor defaults. Derived codecs are
additional APIs; the existing graph binary/Base64 formats are unchanged.

`EvacuationGraphicSpec` and `EvaRoutingSpec` add 27 tests for colors, sprite flags
and frame wrapping, font styles, resource identifiers, router edits, connection
metrics and delays, capacity occupancy, movement snapshots, route selection,
event-driven evacuation and activity history. The initial 24-test run exposed nine
failures. Color fixes remove the JVM Double-to-Integer cast, preserve fractional
hues, keep grayscale opaque and validate conversion bounds. Routing fixes retain
fractional distances, avoid intermediate integer overflow, reject invalid delay
inputs, handle empty choices and return empty occupancy without creating buckets. A later
history test exposed selection by wall-clock event creation time; last activities
now follow simulation time, with the latest history entry breaking equal-time ties.

Remaining worthwhile targets include sprite-map configuration parsing, observable
legacy collections and broader EVA1 multi-person/capacity scenarios. Graph-backed
simulation states/events require an explicit snapshot format that preserves graph
relationships; automatic derivation of mutable controllers, canvas/image handles
and callback-bearing objects is not appropriate. The open `Sprite` hierarchy has
codecs for its concrete built-in values, rather than a closed-world root codec that
would exclude external implementations.

Verification against main `6b20b488`: shared JVM 1,014, shared Scala.js 1,002,
client 511, server 5 and worker 3 tests passed, with no failures or ignored tests.
Both development browser artifacts build successfully.

## Evacuation follow-up: parsing, listener mutation and capacity

This pass starts from main `2fdd2f47`, which includes the earlier audits. Four shared
suites add 35 tests: `SpriteMapConfigSpec` (12), `LegacyObservablesSpec` (10),
`EvaCapacitySpec` (6), and `EvaGraphJsonSpec` (7). They cover sprite variables and
malformed rows, whitespace and comments, atlas coordinate zero, animation ordering
and diagonal fallback; collection/listener snapshots and mutation during callbacks;
shared versus directed corridor occupancy, capacity release and conservation of
three queuing people; and graph/image JSON and MessagePack round trips.

The initial parser/observable run exposed 11 failures. Sprite parsing now accepts
repeated whitespace, trims assignment names/values while retaining embedded equals
signs, uses strict optional integer parsing, validates positive dimensions, includes
atlas ID zero, and skips malformed rows without abandoning valid sprites. Legacy
observable dispatch snapshots its listeners, sequence iterators snapshot their
contents, and absent removals produce no notification. Looking up an unavailable
route no longer inserts a bucket into the supplied routing map.

Default codecs now cover the sealed `EvaImage` description hierarchy and both
concrete variants, generic `PositionableEdge`, and `EvaGraphModel`. The graph JSON
transfer records `SimpleEdge` and `GraphData` have companion-derived writers; their
existing converter aliases remain available as methods. Eager aliases caused an
initialization deadlock when a derived record writer initialized its enclosing
converter. Method aliases remove that cycle, and the default-codec tests initialize
and use the writers without importing the converter's givens.

The legacy graph converter keeps its `nodes`/`edges` wire format. The new default
model codec stores `nodesList`/`edgesList` and restores graph behavior and positioned
edges. `EvaImage` serializes file/path descriptions, including byte contents; it
contains no live canvas or image handle. Mutable graph controllers and complete
simulation state/event snapshots still need a deliberate format.

Verification against main `2fdd2f47`: shared JVM 1,055, shared Scala.js 1,043,
client 512, server 5 and worker 3 tests passed, with no failures or ignored tests.
Both development browser artifacts build successfully.
