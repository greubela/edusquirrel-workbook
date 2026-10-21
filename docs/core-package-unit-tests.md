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
