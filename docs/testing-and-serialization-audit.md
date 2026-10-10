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
