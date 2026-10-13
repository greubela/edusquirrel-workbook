# Shared-core test guide

Shared domain behavior runs on both JVM and Scala.js. Keep tests alongside the
matching production package under `modules/core/shared/src/test/scala`; use
`modules/core/js`/`core/jvm` for platform boundaries and client/worker/server suites
for their adapters. Tests in a parent package can exercise subpackages, so absence
of a same-package spec is not a coverage measurement.

## Finding representative suites

| Area | Suites and behavior |
| --- | --- |
| Storage/cache | `AsyncDataCacheSpec`, `RemoteStoragePackageSpec`, `RemoteCacheControllerSpec`: correlation, reuse, failures, retries and snapshots |
| State/observation | `AsyncDataPackageSpec` and observable/state suites: initial values, failures, ordering, queued derivation and subscription cleanup |
| Synchronization/history | `SyncPackageSpec`, `InteractionHistoryPackageSpec`: event selection, typed history, timestamps and merging |
| VM/programming | `VmTypesPackageSpec`, `ControlStructuresPackageSpec`, `ProgrammingState*Spec`, parser suites: type/AST contracts, conversions and supported execution |
| Collections/geometry | `MatrixPackageSpec`, `GraphPackageSpec`, collection and geometry suites: mutation, independent copies, boundaries and numeric contexts |
| Language/file data | `LanguageSerializationSpec`, `FilePackageSpec`: multilingual resolution, codecs and load failures |
| Evacuation | `EvaRoutingSpec`, `EvaCapacitySpec`, `EvacuationGraphicSpec`, `EvacuationFloorPlanSpec`, `EvacuationExperimentSpec`: routes, capacity, graphics and persisted experiments |
| Workbook domain values | QR, mail, compression, pixel, table, plotting, blockchain and Unicode specs: transitions, validation and task-specific grading |
| Serialization | `SystematicDerivedCodecSpec`, `DefaultValueCodecsSpec`, `AdditionalDefaultCodecsSpec`: JSON/binary round trips and restored behavior |
| Generic execution | `ExecutionInfoIntegrationTest`, JS `WorkerExecutionTest`, worker `BackendWorkerTest`: transport correlation, typed results and failure cleanup |

Use `rg --files modules | rg 'Spec.scala$'` to find the current inventory. These
are representative names, not a frozen class count or a claim of complete coverage.

## Run tests

```sh
npm ci
sbt 'coreJVM/test' 'coreJS/test' 'client/test' 'server/test' 'worker/test'
```

Node dependencies include jsdom for DOM fixtures. Browser checks additionally
require rebuilt client/worker artifacts and Playwright Chromium:

```sh
sbt buildClientDev buildWorkerDev
npx playwright install chromium
npm run test:static
npm run test:workbooks
```

For a narrow shared package:

```sh
sbt 'coreJVM/testOnly *SyncPackageSpec *InteractionHistoryPackageSpec'
sbt 'coreJS/testOnly *SyncPackageSpec *InteractionHistoryPackageSpec'
```

The [CI workflow](../.github/workflows/tests.yml) runs every Scala module, static
checks and browser checks. Domain tests should not require live backend accounts,
network timing or production databases. Use deterministic inputs, in-memory
services, injected schedulers and controlled futures. Restore DOM globals and
cancel owned subscriptions/timers so one suite cannot alter another's environment.

## Coverage and serialization are different checks

[The serialization inventory](testing-and-serialization-audit.md) accounts for
production declarations and explicit exceptions. Behavioral tests must also check
what a decoded model can do, validation failures and protocol compatibility. A
runtime callback or live controller should have an explicit snapshot contract,
not a derived codec added just to satisfy the scanner.

When measuring coverage, report the tool, platform, source scope and current
revision. Line/branch measurements, class inventories and test totals answer
different questions. Do not reuse historical percentages after code moves or API
removal, and do not add artificial assertions just to raise a count.
