# Shared-core test guide

Shared domain behavior runs on both JVM and Scala.js. Keep tests alongside the
matching production package under `modules/core/shared/src/test/scala`; use
`modules/core/js`/`core/jvm` for platform boundaries and client/worker/server suites
for their adapters. Tests in a parent package can exercise subpackages, so absence
of a same-package spec is not a coverage measurement.

## Finding tests

Behavioral suites live beside their production packages in each module's
`src/test/scala/` tree. Shared tests cover domain transitions, validation,
serialization and supported execution on both platforms; adapter tests cover
platform-specific transport and lifecycle behavior.

Use `rg --files modules | rg '(Spec|Test)\.scala$'` for the current inventory.

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

## Keep the suite focused

Keep checks for domain behavior, saved-data compatibility, persistence,
authentication and execution. Browser checks should exercise integration that
unit tests cannot prove; the runner uses two isolated processes and reports each
suite's duration. Use its filename filter for focused runs, for example
`npm run test:workbooks -- codemirror`.

Do not freeze chapter counts, lesson wording, button inventories, CSS dimensions
or source-code spelling in automated tests. Review those when editing content or
presentation. `WorkbookDefinitionsSpec` checks the current catalogue factories
for unique element IDs and serialization without fixing their lesson structure.
Migration-era content snapshots and individual workbook browser tours were removed;
shared domain tests still cover their algorithms, grading and answer models.

CI caches incremental Scala output separately from downloaded dependencies, with
build-definition changes starting a new cache family. Scala still checks the
current sources and test discovery on every run. A cold cache requires a full
compile; deleting fast domain assertions does little to reduce that cost.

The `scala-tests` job runs Scala/static checks, builds the development browser
bundles and saves the compiled output. `browser-tests` downloads those bundles;
when called by the Pages workflow, `pages-build` restores the compiled output and
release-links in parallel with browser checks. Publication waits for the entire
reusable workflow. The required `tests` status also gates Scala, browser and MySQL
checks, including failures or cancellations in upstream jobs.

The MySQL job owns the single server assembly/startup check. Browser bundles and
Pages artifacts use the producing build's attempt-specific name, passed through
job outputs so deploy-only or browser-only retries can reuse the correct upload.

QR encoder tests retain independent full-matrix fixtures for every version and
correction level. Overlapping structure, capacity and rendering assertions use
representative boundaries, including alignment changes and version 32's special
alignment spacing, instead of repeating every combination.

CI test-job durations also include browser installation and compilation; use
individual step/suite timings before removing coverage. Ubuntu package-mirror
delays can dominate Playwright installation and are separate from test execution
and Pages publishing.
