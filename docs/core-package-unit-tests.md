# Shared core package unit tests

Five substantial packages were selected by source size and gaps in direct unit
tests. These are first-party packages in `modules/core/shared/src/main/scala`,
so the same MUnit suites can run on JVM and Scala.js. Package membership here
means an exact Scala package; subpackages are separate packages.

| Package (prefix `it.evadid.`) | Source files | Lines before changes | Named classes, including nested classes | Prior direct tests |
| --- | ---: | ---: | ---: | --- |
| `core.datastructures.storage` | 4 | 837 | 12 | 3 controller tests |
| `vm.types` | 7 | 471 | 23 | None; some indirect parser/language tests |
| `workbook.interaction.sync` | 10 | 428 | 13 | None; controller tests use `SyncSuccess` |
| `vm.code.controlStructures` | 5 | 352 | 6 | None; indirect parser/language tests |
| `core.datastructures.matrix` | 6 | 340 | 6 | None |

The initial five-package pass added 63 test cases across six new suites and the existing
`RemoteCacheControllerSpec`, covering all 60 named classes. This is a class
inventory and behavior audit, not a measured claim of 100% line or branch coverage.
Abstract classes are tested through in-memory implementations. Private request
classes are tested from the storage package; private `DesiredRelevance` is tested
through `SYNC_MAJOR` and `SYNC_MINOR`.

## Class-to-suite inventory

All paths below are relative to `modules/core/shared/src/test/scala/it/evadid/`.

| Suite | Classes exercised |
| --- | --- |
| `core/datastructures/storage/AsyncDataCacheSpec.scala` | `AsyncDataCache`, `SucceededRequest`, `FailedRequest`, `DeletedRequest`, `StartedRequest` |
| `core/datastructures/storage/RemoteStoragePackageSpec.scala` | `RemoteSyncDataCache`, `RemoteCacheCollection`, `CacheCollectionReport`, `DataEntryReadFromServer`, `DataEntryToWriteToServer`, `SyncStatus` |
| `core/datastructures/storage/RemoteCacheControllerSpec.scala` | `RemoteCacheController` |
| `core/datastructures/matrix/MatrixPackageSpec.scala` | `Direction`, `Matrix`, `MatrixDimension`, `MatrixPosition`, `Neighbourhood`, `PositionInMatrix` |
| `vm/types/VmTypesPackageSpec.scala` | `BeChildInfo`, `BodySequence`, `ExpressionInSequence`, `AttributeInClass`, `MethodInClass`, `FunctionParameter`, `ReturnValue`, `ValueForVariable`, `RecentlyInsertedInto`, `BeUnionAllowedTypes`, `BeSerializableAtomicType`, `BeDataTypeAtomic`, `AssigningPossibleWithSameType`, `AssigningPossibleWithImplicitCast`, `AssigningNotPossible`, `BeDataValueUnit`, `BeUseValueReference`, `BeDataValueLiteral`, `BeInfo`, `GlobalScope`, `InFunctionScope`, `InClassScope`, `InSequenceScope` |
| `vm/code/controlStructures/ControlStructuresPackageSpec.scala` | `BeFor`, `BeIfElse`, `BeRepeatNr`, `BeSequenceInfo`, `BeSequence`, `BeWhile` |
| `workbook/interaction/sync/SyncPackageSpec.scala` | `SyncCache`, `SyncContext`, `InteractionVariableSyncReport`, `RichInteractionVariableFormatter`, `InteractionSyncRequest`, `RichInteractionVariableHistorySerialized`, `SyncInformation`, `InteractionVariableFetchResponse`, `SyncFetchedHistory`, `SyncInformationWithContext`, `DesiredRelevance`, `SyncSuccess`, `UsageContext` |

## Behavioral coverage

- Storage: shared pending requests, reuse, forced and age-based reloads, retries,
  deletion, observable updates, batch failures, strict timestamp boundaries,
  missing keys, immutable cache updates, fetch recovery, write failures, collection
  reports and mappings, and controller refresh/error behavior. Promises coordinate
  asynchronous assertions without sleeps or external services.
- Matrix: coordinate arithmetic and distances, direction aliases, neighbourhoods,
  border clipping and wrapping, dimension validation and encoding, immutable
  replacement, insertion/removal, resize overlap, and non-square transposition.
- VM types: literal validation and display, assignment compatibility, union
  intersection, numbered/fixed child roles, ancestor ordering, diagnostics, and
  atomic/union/value/role/scope serialization.
- Control structures: children and scopes, replacement by role, invalid conditions
  and repeat counts, empty bodies, extension points, segmented output, and
  serialization of every concrete control structure.
- Sync: context conversion, counters and timestamps, every sync strategy, plain
  and rich history formatting, destination delegation, synchronous/asynchronous
  errors, batch acknowledgements, selected variable history, typed/unparsed cache
  entries, and partitioning synced/stale/missing destinations.

Existing unimplemented APIs such as `MatrixDimension.positionsClockwise`,
`SyncFormatter2`, and the `BeWhile` simulator executor remain unimplemented;
these tests exercise implemented behavior of their containing classes.

## Defects exposed by the tests

- `RemoteCacheCollection.mapAllAsyncWithKeyAndOutput` consumed its result iterator
  while building the updated cache map, leaving the auxiliary output map empty.
  It now materializes the results once for both maps.
- `BeDataType.allAtomic` was initialized before its atomic values and contained
  nulls. Its registry is now lazy. Every atomic type previously serialized with
  the same class name; new representations use distinct registered type names.
  The ambiguous legacy name `BeDataTypeAtomic` retains its former first-match
  interpretation as `String`; the original type cannot be recovered from that
  old name alone.
- The data type codec attempted to merge string and case-class codecs. It now
  dispatches between their JSON representations explicitly.
- The open `BeExpression` trait's codec merged untagged case-class codecs, which
  failed during initialization. Expression variants now receive explicit tags
  before merging, including definition expressions used by recursive structures.

## Running the suites

Run the complete shared suites on both supported platforms:

```sh
sbt 'coreJVM/test' 'coreJS/test' 'client/test' 'server/test' 'worker/test'
node --test tools/dev/assemble-pages.test.mjs
```

For just the selected packages:

```sh
sbt 'coreJVM/testOnly *AsyncDataCacheSpec *RemoteStoragePackageSpec *RemoteCacheControllerSpec *MatrixPackageSpec *VmTypesPackageSpec *ControlStructuresPackageSpec *SyncPackageSpec'
sbt 'coreJS/testOnly *AsyncDataCacheSpec *RemoteStoragePackageSpec *RemoteCacheControllerSpec *MatrixPackageSpec *VmTypesPackageSpec *ControlStructuresPackageSpec *SyncPackageSpec'
```


## Expanded coverage and restored tests

The follow-up adds dedicated suites for graph mutations, async values/futures/states,
file paths and copyright, naming conventions, colors, users/tokens, interaction
histories, bulk conversion, and default serialization. Tests use real in-memory
objects and controlled futures rather than external services.

Additional defects fixed by these suites:

- Directed graph deletion removed reverse edges; self-loops duplicated nodes and
  edges; replacing an observable node created reverse edges and lost self-loops.
- Async futures lacked `stateNow`, discarded failure metadata, and could remain
  pending when mapping callbacks threw. Observable async states incorrectly failed
  their first-state future instead of returning the typed failed state.
- Local file paths lost directories, URL query/fragment text became extensions,
  and empty paths threw. Parsing now separates authority, directories and filename.
- CamelCase rejected empty names. User-name normalization preserved repeated
  spaces; mail-derived names accidentally treated digits and uppercase letters as
  separators because a regular-expression character class contained a range.
- Secure access-token formatting used `java.util.HexFormat`, which is unavailable
  on Scala.js. Encoding now uses portable hexadecimal digits while retaining
  `SecureRandom` for token generation.
- RGB-to-HSB hue arithmetic truncated fractions. Grayscale HSB colors became
  transparent. Gradient interpolation used invalid numeric casts.
- Sequence edits removed unreplaced children, extension slots used doubled indices,
  and typed empty sequences accessed a nonexistent last child. Scope ancestry
  checked the wrong object's parents.
- Java's dynamic operator matcher evaluated parsers while constructing alternatives.
  Python's indented statements consumed newlines twice.
- Bulk conversion caught `Exception` but not the project's `SerializedException`,
  which extends `Throwable`. It now catches nonfatal failures and propagates fatal
  errors, so corrupted history entries can be partitioned safely.
- Workbook serialization lost reorder constraints and read boolean values as
  strings. Embedded elements now use complete regular-JSON registry descriptors;
  section prerequisites resolve through the registry. Section metadata no longer
  contains an unimplemented decoder.
- Snap custom-block write-back confused an empty `<inputs>` container with actual
  `<input>` declarations and failed to reconstruct legacy input metadata.

### Ignored and commented test decisions

All 18 originally ignored Scala tests were reviewed. Seventeen were restored or
rewritten against the current APIs. The obsolete Turtle test that required built-in
block definitions before calls was removed: built-ins are now represented directly
as VM operations, which adjacent active tests exercise. Python-output expectations
were updated to require the renderer's supported type annotations.

Commented suites were also reviewed. The current Python parser, known VM
structures, language-map integration, workbook-element registry, and sorting-state
serialization tests were migrated and restored. The former vector-shape renderer
suite and `SerializedWorkbook` suite were removed because their APIs no longer
exist; current shape tests remain, and current workbook tests now cover nonempty
section graphs and prerequisites. The commented worker-echo test targeted a removed
method; it was replaced with three tests of the current worker message codec.

### Serialization audit

58 default uPickle codecs were added or exposed for matrix values; path/copyright
models; geometric bounds/dimensions/aspect ratio; graph edges; RGB/HSB colors;
async state/value/failure models; conversion results; serialized exceptions; naming
styles/configuration; sync contexts/cache/strategies; typed interaction histories
and changes; workbook registry descriptors; chat person snapshots and roles;
execution timing; normalized Python statement trees; numeric constraint snapshots;
Turtle project state; and code-check results.

Context-dependent numeric models reconstruct their `Fractional` instance with
explicit tuple codecs. Generic codecs require codecs for their type arguments.
Round-trip tests exercise concrete values, sealed variants, nested trees, optional
values and derived behavior on the reconstructed objects.

The audit intentionally excludes runtime resources such as futures, observers,
subscriptions, callbacks, loggers, UI controls, file handles and executable constraint
functions. `AsyncFuture` is not a persisted snapshot; `AsyncValue` is. Open sync
destinations require the application's registered factory serializer; adding a
universal codec would bypass that registry. This also applies to `SyncInformation`,
`UserConfig` and `AllUserInfo` containing those destinations. Section metadata with
prerequisites must be restored through the workbook registry, which supplies the
referenced sections; direct metadata decoding rejects unresolved references.

Previously serialized types retain their existing application serializers. Newly
exposed default codecs are additive; code already using explicit application
serializers continues using those formats. The atomic-type ambiguity and expression
codec repair are described above.

This work does not establish 100% project line or branch coverage. Existing unfinished
simulator APIs and other explicitly unimplemented features remain outside these
implemented-behavior suites.


## Validation of the first expanded pass

All implemented changes were compiled and tested with sbt 1.9.9 and Java 21.

| Suite | Passed | Failed | Ignored |
| --- | ---: | ---: | ---: |
| `coreJVM/test` | 439 | 0 | 0 |
| `coreJS/test` | 435 | 0 | 0 |
| `client/test` | 384 | 0 | 0 |
| `server/test` | 1 | 0 | 0 |
| `worker/test` | 3 | 0 | 0 |
| **Scala total** | **1,262** | **0** | **0** |

`node --test tools/dev/assemble-pages.test.mjs` also passed (one test file).
`git diff --check` passed. A source scan found no remaining `.ignore` markers or
commented-out Scala test blocks under `modules/**/src/test`.

The final changes add 130 core test cases and three worker message-codec tests,
besides restoring 39 commented core tests and the 17 retained previously ignored
tests. The original five-package inventory covers all 60 named classes; the
follow-up adds dedicated suites in eight further source packages plus cross-package
codec regressions. No percentage of project line or branch coverage is asserted.

The container lacked an installed sbt executable. Validation used a downloaded
sbt launcher with a local Maven Central cache. Its temporary launcher and logs are
kept outside the repository in `/workspace/work/edusquirrel-unit-test-support`.
Compiler deprecation/unchecked warnings and font-cache warnings occurred; none
caused a failing test.


## Follow-up coverage

The next pass adds 24 core tests: 13 serializer-helper tests, four numeric-constraint
tests, four chat tests, two async-recovery tests, and one malformed workbook-registry
test. The tests cover empty/nested projections, significant string whitespace,
canonical and legacy numeric formats, error causes and fatal-error propagation,
constraint intersections and snapshots, backdated messages, chat wire compatibility,
and unresolved embedded references.

Further fixes:

- `optionProjectionIO` and `eitherProjectionIO` truncated payloads. The either
  encoder also omitted the branch wrappers its reader requires. Projection codecs
  now preserve complete nested payloads and reject malformed wrappers.
- Numeric helpers now emit valid `0b`, `0x`, and `0o` prefixes with the minus sign
  before the prefix. Hexadecimal uses base 16. Legacy `Ob`, `O`, and `Ox` remain
  readable; historically octal `Ox` output is decoded in base 8 to preserve old
  stored values. Java octal literals use their language's leading-zero syntax.
- Constructor-like wrapper parsing used the untrimmed string length after trimming
  whitespace. Optional string decoding removed significant payload whitespace.
- Safe sequence decoding handles nonfatal `SerializedException` failures. Mapped
  serializers preserve original error causes; serializer adapters propagate fatal
  errors instead of treating them as malformed input.
- `ValueDependentConstraints` now intersects bounds using the strongest minimum
  and maximum, evaluating each function once per returned snapshot. Contradictory
  limits remain visible to callers; callback functions are not serialized.
- Workbook registries are expanded once per element ID, so unresolved references
  and dependency cycles cannot endlessly reinsert embedded descriptors.
- Chat insertion sorts the new message together with existing messages. Scaffolding
  no longer discards a literal backslash-s answer by replacing that character pair.
- Async recovery now turns synchronous callback exceptions into finished failures.

`Message` and `MessengerModel` now expose default uPickle codecs by forwarding the
existing application codecs. This brings the added/exposed default codec count to
60 while preserving chat's existing JSON format. Authors retain the existing
`SerializablePerson` snapshot representation; runtime subclasses are not persisted.


### Follow-up validation (2026-10-08)

| Suite | Passed | Failed | Ignored |
| --- | ---: | ---: | ---: |
| `coreJVM/test` | 463 | 0 | 0 |
| `coreJS/test` | 459 | 0 | 0 |
| `client/test` | 384 | 0 | 0 |
| `server/test` | 1 | 0 | 0 |
| `worker/test` | 3 | 0 | 0 |
| **Scala total** | **1,310** | **0** | **0** |

All five suites were run through sbt after these changes. `git diff --check`
passed. The cumulative changes now add 154 core tests and three worker tests,
with 60 added/exposed default codecs. Coverage percentages remain unmeasured;
the unfinished-feature limitations above still apply.

## Measured coverage pass (2026-10-08)

Added `sbt-scoverage` 2.4.4 and measured `coreJVM` with Scala 3.8.4. This scope
includes shared core and JVM-specific production sources exercised by the core JVM
suite. It does **not** measure client, worker, server, or Scala.js execution.
No package/file exclusions were added: legacy `evacuation` sources and unfinished
code are included. Coverage counts instrumented statements and branches, not lines
or a count of classes with tests. Declaration-only classes may have no statements.

| Core JVM metric | Before | After |
| --- | ---: | ---: |
| Statement coverage | 49.78% (12,073 / 24,253) | 55.19% (13,421 / 24,316) |
| Branch coverage | 49.00% (1,423 / 2,904) | 53.75% (1,569 / 2,919) |
| Passing test cases | 463 | 511 |

The increase is **5.41 percentage points** for statements and **4.75 percentage
points** for branches. Production fixes changed the instrumented denominator;
both actual counts are shown above. The baseline report was captured before this
pass's tests and fixes. The final run cleared measurement files and executed the
entire core JVM suite so failed exploratory runs did not contribute coverage.

| Exact package (prefix `it.evadid.`) | Statement coverage before | After |
| --- | ---: | ---: |
| `core.datastructures.vectorShapes.svg` | 31.00% | 80.31% |
| `core.util` | 12.57% | 97.99% |
| `core.datastructures.tree` | 23.33% | 78.89% |
| `core.datastructures.tree.nodeImpl` | 77.92% | 95.24% |

### Added portable suites and defects reproduced

All 48 new test cases are in shared core and run on JVM and Scala.js:

- `SvgPathBuildersSpec` (16): mutable/immutable endpoints, curves, arc flags,
  close-path/subpath semantics, live bounds, translation, command conversions,
  drawing helpers and percentage coordinates. Fixed empty immutable paths throwing
  from `current`, close-path failing to restore the latest subpath start in both
  builders, and mutable bounds remaining cached after edits.
- `SvgPathParserCoverageSpec` (10): repeated coordinates, axis commands, curves,
  arcs, closed subpaths, compact signed/decimal/exponent numbers and malformed
  inputs. Fixed valid compact numbers being rejected, incomplete commands being
  silently replaced, and invalid/non-finite numbers, arc flags and radii being
  accepted. Unsupported `S`/`T` commands still return `None`; this does not claim
  support for every SVG path grammar feature.
- `UtilityCoverageSpec` (5): shared dependency caching, retries, cycles, code
  formatting and date formats. Fixed failed calculations leaving entries in the
  active dependency stack, causing retries to report a spurious cycle. Cleanup
  runs in `finally` while successfully calculated dependencies remain cached.
- `TreeCoverageSpec` (7): empty/missing positions, traversal order, bulk insertion,
  subtree insertion, pruning, cached contextual mapping and position helpers.
  Fixed unsorted bulk insertions overwriting earlier inserted siblings; insertion
  indices refer to the original child list and equal indices retain caller order.
- `MarkdownCoverageSpec` (10): paragraphs, line endings/breaks, lists, quotes,
  fences, escaped punctuation, inline code, links/images and literal placeholder
  text. Fixed escaped punctuation being formatted, code backslashes being lost,
  placeholder collisions, double-escaped link attributes, emphasis rewriting
  attributes, markup in image alt attributes and dollar signs being interpreted
  as regex replacement groups. Existing client Markdown tests remain active.

### Reproduce the measurement

From the repository root with Java 21 and sbt available:

```sh
sbt 'set LocalProject("coreJVM") / coverageEnabled := true' \
    'coreJVM/clean' 'coreJVM/test' 'coreJVM/coverageReport'
```

`clean` prevents earlier test measurements from entering the report. The setting
is limited to that sbt session; ordinary builds retain coverage disabled. Reports:

- HTML: `target/coreJVM/scala-3.8.4/scoverage-report/index.html`
- XML: `target/coreJVM/scala-3.8.4/scoverage-report/scoverage.xml`
- Cobertura: `target/coreJVM/scala-3.8.4/coverage-report/cobertura.xml`

For normal regression validation, start a new sbt session:

```sh
sbt 'coreJVM/test' 'coreJS/test' test
node --test tools/dev/assemble-pages.test.mjs
```

The remaining uncovered statements include legacy evacuation graph/routing code,
the C++ parser, language serialization and JVM canvas code. These measurements
identify further work; they do not establish full-project coverage or complete
correctness.

### Final regression validation for this pass

Coverage instrumentation was disabled before the normal build and test run.

| Task | Passed | Failed | Ignored |
| --- | ---: | ---: | ---: |
| `coreJVM/test` | 511 | 0 | 0 |
| `coreJS/test` | 507 | 0 | 0 |
| `client/test` | 384 | 0 | 0 |
| `server/test` | 1 | 0 | 0 |
| `worker/test` | 3 | 0 | 0 |
| **Scala total** | **1,406** | **0** | **0** |

The instrumented core JVM run also passed all 511 tests. The Node assembly test
file and `git diff --check` passed. Compiler/font-cache warnings were non-failing.

## Language serialization follow-up (2026-10-08)

Added `LanguageSerializationSpec` with 20 shared test cases, using in-memory file
sources without network or filesystem dependencies. It covers human/universal
JSON translations, UTF-8, empty files, quoted/multiline CSV fields, malformed
input, Snap key mapping, file-type and directory factories, source collections,
reference-table resolution, fallback maps and default codec round-trips.

The tests reproduced and fixed:

- Translation triples added literal quotes when decoded and failed to escape
  quotes, newlines and backslashes when encoded. Their existing constructor/string
  wire format is retained; the text payload now uses JSON string encoding/decoding.
  Wrong payload types and extra/missing constructor payloads are rejected.
- File loaders threw synchronous errors outside their returned futures and used
  an incomplete exception match for `SerializedException`. They now convert
  synchronous failures to futures and recover non-fatal load errors consistently.
- Source collections failed entirely on synchronous errors or non-`Exception`
  failures such as `SerializedException`. They now isolate non-fatal failures and
  retain successful translations from other sources.

Default uPickle ReadWriters were added/exposed for `LanguageMapEntry[T]`,
`LanguageTripel` and `ParsedTriplesSerialized`, bringing this work's codec additions
and exposures to 63. Existing triple/table wire formats are preserved. Runtime
sources containing file handles, execution contexts or callbacks are not assigned
misleading automatic codecs. `LanguageMapLocalStorageSourceInfo.loadAllTriples`
remains an unfinished API; it was not implemented merely to increase coverage.

A fresh core JVM measurement, with the same source scope and no added exclusions,
produced the following results:

| Metric | Previous measured pass | Language serialization follow-up |
| --- | ---: | ---: |
| Statements | 55.19% (13,421 / 24,316) | 56.59% (13,772 / 24,335) |
| Branches | 53.75% (1,569 / 2,919) | 55.13% (1,611 / 2,922) |
| Language serialization package statements | 0.00% | 82.43% |
| Language serialization abstractions package statements | 0.00% | 96.06% |

These changes increase statements by 1.40 percentage points and branches by 1.38
percentage points beyond the previous pass. Since the first measurement, core JVM
statement coverage has increased from 49.78% to 56.59%, and branch coverage from
49.00% to 55.13%. Measurement files were cleared before running the full suite.

Final normal validation, after disabling instrumentation, passed all **1,446 Scala
tests**: core JVM 531, core Scala.js 527, client 384, server 1 and worker 3. There
were no failures or ignored tests. The instrumented JVM run also passed all 531
tests; the Node assembly test and `git diff --check` passed.
