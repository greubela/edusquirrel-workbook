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

The MySQL integration suite needs a disposable MySQL 8.4, for example in Docker
(the variables match the CI job):

```sh
docker run -d --name edu-mysql -p 3306:3306 -e MYSQL_DATABASE=edusquirrel_account_test \
  -e MYSQL_USER=account_test -e MYSQL_PASSWORD=disposable -e MYSQL_RANDOM_ROOT_PASSWORD=yes mysql:8.4
SQL_HOST=127.0.0.1 SQL_PORT=3306 SQL_DATABASE=edusquirrel_account_test SQL_USER=account_test \
  SQL_PW=disposable SERVER_SECRET=disposable sbt 'server / MysqlIntegration / test'
```

`ProgrammingUnitTestGraderSpec` (coreJVM) grades answers through the worker's
unit-test harness in a local `python3` via the test-only `CPythonRuntime` client; it
is skipped when no `python3` is installed.

Core coverage runs on both platforms through [core-coverage.yml](../.github/workflows/core-coverage.yml).
Each report must exceed 60% branch coverage; CI uploads the reports and does not
reuse instrumented classes in normal build caches. Run locally with
`sbt 'project coreJVM' coverage test coverageReport` (or `coreJS`).

For a narrow shared package:

```sh
sbt 'coreJVM/testOnly *RemoteHistoryCacheSpec *InteractionHistoryPackageSpec'
sbt 'coreJS/testOnly *RemoteHistoryCacheSpec *InteractionHistoryPackageSpec'
```

The [CI workflow](../.github/workflows/tests.yml) runs every Scala module, static
checks and browser checks. Domain tests should not require live backend accounts,
network timing or production databases. Use deterministic inputs, in-memory
services, injected schedulers and controlled futures. Restore DOM globals and
cancel owned subscriptions/timers so one suite cannot alter another's environment.

## Coverage and serialization are different checks

Only types crossing a [serialization boundary](serialization-boundaries.md) have
codecs, and the compiler rejects a boundary type without one. Round-trip tests
show that a codec encodes; behavioral tests must also check what a decoded model
can do, validation failures and protocol compatibility.

When measuring coverage, report the tool, platform, source scope and current
revision. Line/branch measurements, class inventories and test totals answer
different questions. Do not reuse historical percentages after code moves or API
removal, and do not add artificial assertions just to raise a count.

## Keep the suite focused

Keep checks for domain behavior, saved-data compatibility, persistence,
authentication and execution. Browser checks should exercise integration that
unit tests cannot prove. The runner runs two isolated suites at a time, reports each
suite's duration and uses Playwright's Chromium, or `CHROMIUM_PATH` when set. It serves
CodeMirror from pinned local npm packages, so editor tests need no live CDN. Use its
filename filter for focused runs, for example `npm run test:workbooks -- codemirror`;
single files also run with `node --test`.

Do not freeze chapter counts, lesson wording, button inventories, CSS dimensions
or source-code spelling in automated tests. Review those when editing content or
presentation. `WorkbookDefinitionsSpec` checks the current catalogue factories
for unique element IDs and serialization without fixing their lesson structure.

CI ([tests.yml](../.github/workflows/tests.yml)) runs JVM and Scala.js tests on parallel
runners, links browser bundles from the cached Scala.js output and gates everything behind the
required `tests` status; the workflow file is the reference for jobs and caches. Most of
`core`'s compile time is uPickle codec derivation, so deleting fast assertions barely helps.
Run MySQL tests only against a disposable localhost database: the suite clears its tables and
refuses any database other than `edusquirrel_account_test` on localhost.
